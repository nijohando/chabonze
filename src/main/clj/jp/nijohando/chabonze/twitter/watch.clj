(ns jp.nijohando.chabonze.twitter.watch
  (:require
    [jp.nijohando.chabonze.core.slack :as slack]
    [jp.nijohando.chabonze.twitter.conf :as conf]
    [jp.nijohando.chabonze.core.store :as store]
    [jp.nijohando.chabonze.core.util :as util]
    [jp.nijohando.chabonze.twitter.auth :refer [sign]]
    [jp.nijohando.chabonze.twitter.lists :as lists]
    [jp.nijohando.failable :refer [ftry flet f->> f-> fail guard when-failure]]
    [clojure.tools.logging :as log]
    [clojure.string :as string]
    [clojure.tools.cli :refer [parse-opts]]
    [clj-http.client :as http]
    [environ.core :refer [env]]
    [mount.core :refer [defstate]])
  (:import (java.util.concurrent Executors
                                 ScheduledFuture
                                 ScheduledExecutorService
                                 TimeUnit)))

(declare state)

(defn- cancel-task
  [task-id ^ScheduledFuture f]
  (ftry
    (when (and f (not (.isDone f)) (not (.isCancelled f)))
      (.cancel f)
      (log/info "Task '" task-id "' cancelled."))))

(defn- tasks
  []
  (->> (for [[slug {:keys [channels]}] (store/get-in [:twitter :lists])]
         (->> (for [[ch-id {:keys [task-id]}] channels]
                [task-id [:twitter :lists slug :channels ch-id]])
              (into {})))
       (apply merge)))

(defn- has-task?
  [task-id]
  (contains? (tasks) task-id))

(defn- invoke-api-statuses
  [list-name since-id]
  (flet [oauth-token (store/get-in [:twitter :oauth :token-secret])
         req (sign {:url "https://api.twitter.com/1.1/lists/statuses.json"
                    :query-params (merge {:slug list-name :owner_screen_name "nijohando"} (when since-id {:since_id since-id}))
                    :method "GET"
                    :as :json})]
    (http/request req)))

(defn- now
  []
  (System/currentTimeMillis))

(defn- fetch-new-statuses
  [slug channel]
  (flet [since-id (store/get-in [:twitter :lists slug :channels channel :since-id])
         {:keys [body] :as res} (invoke-api-statuses slug since-id)]
    (when (not-empty body)
      (let [latest-id (-> body first :id_str)
            statuses (reverse body)]
        (doseq [status statuses]
          (let [id (:id_str status)
                screen-name (get-in status [:user :screen_name])
                user-name (get-in status [:user :name])
                text (str "https://twitter.com/" screen-name "/status/" id)
                icon-url (get-in status [:user :profile_image_url_https])]
            (slack/post-message {:channel channel
                                 :username (str screen-name " (via " (store/get-in [:slack :self :name]) ")")
                                 :text text
                                 :icon_url icon-url})))
        (store/transact!
          (fn [current]
            (assoc-in current [:twitter :lists slug :channels channel :since-id] latest-id)))))))

(defn- watch-future-updater
  [task-id slug interval channel]
  (fn [^ScheduledFuture f]
    (let [executor (:executor @state)]
      (when-failure (cancel-task task-id f)
        (log/error "Failed to cancel task " + task-id + " " f))
      (.scheduleWithFixedDelay executor
                               #(let [result (fetch-new-statuses slug channel)]
                                           (when-failure result
                                             (log/error "Failed to fetch list of statuses." result)))
                               1 (* interval 60) (TimeUnit/SECONDS)))))

(defn- perform-list
  [slack-msg]
  (flet [_ (slack/typing slack-msg)
         response (lists/invoke-api-list)
         slugs (->> response
                         :body
                         (map :slug))
         chs (slack/channels)
         get-stat (fn [slug]
                    (for [[ch-id task] (store/get-in [:twitter :lists slug :channels])
                          :let [ch-name (get-in chs [ch-id :name])]]
                      {:channel (str "#" ch-name) :task-id (:task-id task) :interval (:interval task)}))
         result (loop [[slug & more] slugs result []]
                  (if-not slug
                    result
                    (recur more (concat result (map (fn [m] (merge m {:slug slug})) (get-stat slug))))))
         text (str "```" (util/tabular-text (array-map :task-id "TASK-ID"  :slug "SLUG" :channel "CHANNEL" :interval "INTERVAL") result) "```")]
      (slack/reply-message slack-msg text)))

(defn- perform-add
  [slack-msg slug interval]
  (flet [_ (slack/typing slack-msg)]
    (store/transact!
      (fn [current]
        (flet [channel (:channel slack-msg)
               updated (update-in current [:twitter :task-id-seq] (fnil inc 0))
               task-id (get-in updated [:twitter :task-id-seq])]
          (send-off state
                    #(update-in % [:tasks task-id] (watch-future-updater task-id slug interval channel)))
          (update-in updated [:twitter :lists slug :channels channel] merge {:task-id task-id :created-at (now) :interval interval}))))))

(defn- perform-delete
  [slack-msg task-id]
  (flet [_ (slack/typing slack-msg)
         k (get (tasks) task-id)]
    (store/transact!
      (fn [current]
        (send-off state #(if-let [f (get-in % [:tasks task-id])]
                           (do
                             (when-failure (cancel-task task-id f)
                               (log/error "Failed to cancel task " task-id " " f))
                             (let [updated (update % :tasks dissoc task-id)]
                               (slack/reply-message slack-msg (str "Task `" task-id "` is cancelled."))
                               updated))
                           %))
        (update-in current (butlast k) dissoc (last k))))))

(def ^:private command-options
  [["-l" "--list" "show watch tasks"]
   ["-a" "--add SLUG" "add watch task"
    :validate [#(lists/exists? %) "list not found."]]
   ["-i" "--interval MINUTES" "watch interval"
    :default 10
    :parse-fn #(Long/parseLong %)]
   ["-d" "--delete TASK-ID" "delete watch task"
    :validate [#(has-task? %) "task not found."]
    :parse-fn #(Long/parseLong %)]
   ["-h" "--help"]])

(defn- usage
  [option-summary]
  (->> ["```"
        "Usage: /twitter watch -l"
        "   or: /twitter watch -a <SLUG> -i <INTERVAL>"
        "   or: /twitter watch -d <TASK-ID>"
        ""
        "Options:"
        option-summary
        "```"]
       (string/join \newline)))

(defn- error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn- validate-args
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args command-options)
        has-option? #(contains? options %)]
    (cond
      (:help options) {:exit-message (usage summary)}
      (> (count (select-keys options [:list :add :delete])) 1) {:exit-message (usage summary)}
      errors {:exit-message (str (error-msg errors) "\n\n" (usage summary))}
      (has-option? :list) {:action :list}
      (has-option? :add) {:action :add :options options}
      (has-option? :delete) {:action :delete :options options}
      :else {:exit-message (usage summary)})))

(defn execute
  [slack-msg [_ & args]]
  (flet [{:keys [action options exit-message]} (validate-args args)]
    (if exit-message
      (slack/reply-message slack-msg exit-message)
      (condp = action
        :list (perform-list slack-msg)
        :add (perform-add slack-msg (:add options) (:interval options))
        :delete (perform-delete slack-msg (:delete options))))))

(defn start
  []
  (flet [executor (Executors/newScheduledThreadPool 1)
         wls (store/get-in [:twitter :lists])
         state (agent {:executor executor :tasks {}})]
    (doseq [[slug {:keys [channels]}] wls]
      (doseq [[channel-id {:keys [task-id interval]}] channels]
        (send-off state #(update-in % [:tasks task-id] (watch-future-updater task-id slug interval channel-id)))))
    state))

(defn stop
  [{:keys [executor]}]
  (ftry
    (when executor
      (.shutdown executor))))

(defstate state
          :start (-> (start)
                     (guard "Failed to start"))
          :stop (-> (stop @state)
                    (guard "Failed to stop")))

