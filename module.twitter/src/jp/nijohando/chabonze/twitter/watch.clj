(ns jp.nijohando.chabonze.twitter.watch
  (:require
   [clojure.string :as string]
   [clojure.tools.cli :as cli]
   [clojure.core.async :as ca]
   [integrant.core :as ig]
   [duct.logger :as dl]
   [clj-http.client :as http]
   [jp.nijohando.chabonze.bot.slack.rtm :as slack.rtm]
   [jp.nijohando.chabonze.bot.slack.web :as slack.web]
   [jp.nijohando.chabonze.bot.store :as st]
   [jp.nijohando.chabonze.bot.util :as util]
   [jp.nijohando.chabonze.bot.command :as cmd]
   [jp.nijohando.chabonze.twitter.list :as twitter.list]
   [jp.nijohando.chabonze.twitter.search :as twitter.search]
   [jp.nijohando.failable :as f]
   [jp.nijohando.deferable :as d]
   [jp.nijohando.event :as ev]
   [jp.nijohando.event.timer :as tm]
   [jp.nijohando.ext.async :as xa]))

(defprotocol Service
  (-start [this])
  (-stop [this]))

(defn- now
  []
  (System/currentTimeMillis))

(defn- parse-query
  [q]
  (->> (string/split q #"\s+")
       (reduce (fn [ctx x]
                 (let [[k v] (string/split x #":")]
                   (if (and v (= "lang" k))
                     (assoc ctx :lang v)
                     (update ctx :strings conj x)))) {:strings []})))

(defn- service
  [{:keys [rtm web logger store twlist twsearch] :as opts}]
  (let [timer (tm/timer)
        watches (agent {})]
    (letfn [(fetch-tweets [task-id]
              (if-let [{:keys [list search since-id] :as task} (get-in @store [:twitter :watch :tasks task-id])]
                (f/if-succ [x (cond
                                (some? list) (twitter.list/get-tweets twlist (:id list) since-id)
                                (some? search) (twitter.search/get-tweets twsearch (:query search) since-id))]
                  (when-not (empty? x)
                    (let [latest-id (-> x first :id_str)
                          tweets (reverse x)]
                      (doseq [tweet tweets]
                        (let [id (:id_str tweet)
                              screen-name (get-in tweet [:user :screen_name])
                              url (str "https://twitter.com/" screen-name "/status/" id)
                              icon-url (get-in tweet [:user :profile_image_url_https])]
                          (slack.web/post-message web {:channel (get-in task [:channel :id])
                                                 :username (str screen-name " (via " (get-in @store [:slack :self :name]) ")")
                                                 :text url
                                                 :icon_url icon-url})))
                      (st/transact! store assoc-in [:twitter :watch :tasks task-id :since-id] latest-id)))
                  x)
                (do
                  (tm/cancel! timer task-id)
                  (-> (f/fail ::task-not-found)
                      (assoc :task-id task-id)))))
            (register-timer [{:keys [task-id interval] :as task}]
              (tm/repeat! timer (* interval 60 1000) 0 (ev/event (str "/watches/" task-id))))
            (registered-list? [channel slug]
              (->> (get-in @store [:twitter :watch :tasks])
                   vals
                   (filter #(and (= channel (get-in % [:channel :id]))
                                 (= slug (get-in % [:list :slug]))))
                   empty?
                   not))
            (registered-query? [channel query]
              (->> (get-in @store [:twitter :watch :tasks])
                   vals
                   (filter #(and (= channel (get-in % [:channel :id]))
                                 (= query (get-in % [:search :query]))))
                   empty?
                   not))
            (get-list [slug]
              (f/if-succ [x (f/succ->> (twitter.list/get-lists twlist)
                                       (filter #(= slug (:slug %)))
                                       (first))]
                (if (empty? x)
                  (-> (f/fail ::list-not-found)
                      (assoc :slug slug))
                  x)
                x))
            (has-task? [task-id]
              (-> (get-in @store [:twitter :watch :tasks])
                  (contains? task-id)))
            (validate-args [args]
              (let [cli-opts [["-l" "--list" "show watch tasks"
                               :id :list]
                              ["-a" "--add-list SLUG" "add list watch task"
                               :id :add-list
                               :validate [#(twitter.list/exists? twlist %) "list not found."]]
                              ["-A" "--add-query QUERY" "add query watch task"
                               :id :add-query
                               :parse-fn parse-query]
                              ["-i" "--interval MINUTES" "watch interval"
                               :validate [#(>= % 1) "interval must be greater than 0"]
                               :default 10
                               :parse-fn #(Long/parseLong %)]
                              ["-d" "--delete TASK-ID" "delete watch task"
                               :id :delete
                               :validate [#(has-task? %) "task not found."]]
                              ["-h" "--help"]]
                    {:keys [options arguments errors summary]} (cli/parse-opts args cli-opts)
                    has-option? #(contains? options %)
                    usage (fn [option-summary]
                            (->> ["```"
                                  "Usage: /twitter watch -l"
                                  "   or: /twitter watch -a <SLUG> -i <INTERVAL>"
                                  "   or: /twitter watch -A <QUERY> -i <INTERVAL>"
                                  "   or: /twitter watch -d <TASK-ID>"
                                  ""
                                  "Options:"
                                  option-summary
                                  "```"]
                                 (string/join \newline)))
                    error-msg (fn [errors]
                                (str "The following errors occurred while parsing your command:\n\n"
                                     (string/join \newline errors)))]
                (cond
                  (:help options) {:exit-message (usage summary)}
                  (-> (select-keys options [:list :add-list :add-query :delete])
                      keys
                      count
                      (> 1)) {:exit-message (usage summary)}
                  errors {:exit-message (str (error-msg errors) "\n\n" (usage summary))}
                  (has-option? :add-list) {:action :add-list :options options}
                  (has-option? :add-query) {:action :add-query :options options}
                  (has-option? :list) {:action :list}
                  (has-option? :delete) {:action :delete :options options}
                  :else {:exit-message (usage summary)})))
            (add-query-watch-task [{channel-id :channel :as msg} {query :add-query interval :interval :as cli-opts}]
              (f/slet [{channel-name :name} (slack.web/channel web channel-id)]
                (st/transact! store (fn [state]
                                      (let [updated (update-in state [:twitter :watch :task-id-seq] (fnil inc 0))
                                            task-id (-> (get-in updated [:twitter :watch :task-id-seq])
                                                        str)
                                            task {:task-id task-id
                                                  :search {:query query}
                                                  :channel {:id channel-id
                                                            :name channel-name}
                                                  :created-at (now)
                                                  :interval interval}]
                                        (send watches (fn [state]
                                                        (->> (register-timer task)
                                                             (assoc state task-id))))
                                        (update-in updated [:twitter :watch :tasks task-id] merge task))))))
            (add-list-watch-task [{channel-id :channel :as msg} {slug :add-list interval :interval :as cli-opts}]
              (f/slet [{list-id :id :as x} (get-list slug)
                       {channel-name :name} (slack.web/channel web channel-id)]
                (st/transact! store (fn [state]
                                      (let [updated (update-in state [:twitter :watch :task-id-seq] (fnil inc 0))
                                            task-id (-> (get-in updated [:twitter :watch :task-id-seq])
                                                        str)
                                            task {:task-id task-id
                                                  :list {:id list-id
                                                         :slug slug}
                                                  :channel {:id channel-id
                                                            :name channel-name}
                                                  :created-at (now)
                                                  :interval interval}]
                                        (send watches (fn [state]
                                                        (->> (register-timer task)
                                                             (assoc state task-id))))
                                        (update-in updated [:twitter :watch :tasks task-id] merge task))))))
            (add-list-command [{:keys [channel] :as msg} {slug :add-list :as cli-opts}]
              (slack.rtm/send-typing rtm channel)
              (if (registered-list? channel slug)
                (slack.rtm/send-message rtm channel (str "Twitter list `" slug "` is already watching on this channel."))
                (f/if-succ [x (add-list-watch-task msg cli-opts)]
                  (slack.rtm/send-message rtm channel (str "Watching twitter list `" slug "` on this channel."))
                  (do
                    (dl/log logger :error :failed-to-add-list-watch-task x)
                    (slack.rtm/send-message rtm channel (str "Opps! Failed to add watch task."))))))
            (add-query-command [{:keys [channel] :as msg} {query :add-query :as cli-opts}]
              (slack.rtm/send-typing rtm channel)
              (if (registered-query? channel query)
                (slack.rtm/send-message rtm channel (str "Twitter search `" query "` is already watching on this channel."))
                (f/if-succ [x (add-query-watch-task msg cli-opts)]
                  (slack.rtm/send-message rtm channel (str "Watcing twitter search `" query "` on this channel."))
                  (do
                    (dl/log logger :error :failed-to-add-query-watch-task x)
                    (slack.rtm/send-message rtm channel (str "Opps! Failed to add watch task."))))))
            (list-command [{:keys [channel] :as msg} cli-opts]
              (slack.rtm/send-typing rtm channel)
              (let [xs (->> (get-in @store [:twitter :watch :tasks])
                            vals
                            (map (fn [x] (merge (select-keys x [:task-id :interval])
                                                {:channel (get-in x [:channel :name])
                                                 :type (if (contains? x :list) "list" "query")
                                                 :target (or (get-in x [:list :slug])
                                                                (get-in x [:search :query]))}))))]
                (->> (str "```"
                          (util/tabular-text (array-map :task-id "TASK-ID"
                                                        :channel "CHANNEL"
                                                        :type "TYPE"
                                                        :target "TARGET"
                                                        :interval "INTERVAL(min)") xs)
                          "```")
                     (slack.rtm/send-message rtm channel))))
            (delete-command [{:keys [channel] :as msg} {task-id :delete :as cli-opts}]
              (slack.rtm/send-typing rtm channel)
              (tm/cancel! timer task-id)
              (st/transact! store update-in [:twitter :watch :tasks] dissoc task-id)
              (dl/log logger :info :task-deleted {:task-id task-id})
              (slack.rtm/send-message rtm channel (str "Task `"  task-id "` deleted.")))]
      (reify
        cmd/Command
        (name [this]
          "watch")
        (description [this]
          "Watch lists or search result on the channel.")
        (execute [this {:keys [channel] :as msg} args]
          (let [{:keys [action exit-message ok?] cli-opts :options} (validate-args args)]
            (if exit-message
              (slack.rtm/send-message rtm channel exit-message)
              (condp = action
                :add-list (add-list-command msg cli-opts)
                :add-query (add-query-command msg cli-opts)
                :list (list-command msg cli-opts)
                :delete (delete-command msg cli-opts)))))
        Service
        (-start [this]
          (doseq [{:keys [task-id] :as task} (-> (get-in @store [:twitter :watch :tasks]) vals)]
            (send watches (fn [state]
                            (->> (register-timer task)
                                 (assoc state task-id)))))
          (d/do** done
            (let [listener (ca/chan 1)
                  _ (d/defer (ca/close! listener))]
              (ev/listen timer "/watches/:id" listener)
              (ca/go-loop []
                (let [x (xa/<! listener)]
                  (if (nil? x)
                    (done)
                    (let [task-id (get-in x [:header :route :path-params :id])]
                      (dl/log logger :debug :retrieve-tweets {:task-id task-id})
                      (ca/thread
                        (f/when-fail [x (fetch-tweets task-id)]
                          (dl/log logger :error :failed-to-fetch-tweets x)))
                      (recur))))))))
        (-stop [this]
          (ev/close! timer))))))

(defmethod ig/init-key :jp.nijohando.chabonze.twitter/watch [_ options]
  (let [srv (service options)]
    (-start srv)
    srv))

(defmethod ig/halt-key! :jp.nijohando.chabonze.twitter/watch [_ srv]
  (-stop srv))

