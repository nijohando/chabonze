(ns jp.nijohando.chabonze.twitter.list
  (:require
   [clojure.string :as string]
   [clojure.tools.cli :as cli]
   [integrant.core :as ig]
   [duct.logger :as dl]
   [clj-http.client :as http]
   [jp.nijohando.chabonze.bot.slack.rtm :as slack.rtm]
   [jp.nijohando.chabonze.bot.util :as util]
   [jp.nijohando.chabonze.bot.command :as cmd]
   [jp.nijohando.chabonze.twitter.auth :as twitter.auth]
   [jp.nijohando.failable :as f]))

(defprotocol Service
  (-get-lists [this])
  (-get-tweets [this list-id since-id]))

(defn exists?
  [srv slug]
  (f/slet [slugs (f/succ->> (-get-lists srv)
                            (map :slug)
                            (into #{}))]
    (contains? slugs slug)))

(defn get-lists
  [srv]
  (-get-lists srv))

(defn get-tweets
  [srv list-id sice-id]
  (-get-tweets srv list-id sice-id))

(defn- validate-args
  [args]
  (let [cli-opts [["-h" "--help"]]
        {:keys [options arguments errors summary]} (cli/parse-opts args cli-opts)
        has-option? #(contains? options %)
        usage (fn [option-summary]
                (->> ["```"
                      "Usage: /twitter list"
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
      errors {:exit-message (str (error-msg errors) "\n\n" (usage summary))}
      :else {:action :lists})))

(defn- service
  [{:keys [rtm logger twauth connect-timeout socket-timeout]
    :or {connect-timeout 1000
         socket-timeout 1000}
    :as opts}]
  (letfn [(get-lists []
            (f/succ->>*
             {:url "https://api.twitter.com/1.1/lists/list.json"
              :method "GET"
              :socket-timeout socket-timeout
              :conn-timeout connect-timeout
              :as :json}
             (twitter.auth/sign twauth)
             http/request
             :body))
          (get-tweets [list-id since-id]
            (f/succ->>*
             {:url "https://api.twitter.com/1.1/lists/statuses.json"
              :query-params (merge {:list_id list-id} (when since-id {:since_id since-id}))
              :method "GET"
              :socket-timeout socket-timeout
              :conn-timeout connect-timeout
              :as :json}
             (twitter.auth/sign twauth)
             http/request
             :body))
          (lists-command [{:keys [channel] :as msg}]
            (slack.rtm/send-typing rtm channel)
            (f/if-succ [x (f/succ->> (get-lists)
                                     (map #(select-keys % [:slug :name :mode :member_count])))]
              (do
                (->> (str "```"
                          (util/tabular-text (array-map :slug "SLUG"
                                                        :mode "MODE"
                                                        :member_count "MEMBERS"
                                                        :name "NAME") x)
                          "```")
                     (slack.rtm/send-message rtm channel)))
              (do
                (dl/log logger :error :failed-to-get-twitter-lists x)
                (slack.rtm/send-message rtm channel (str "Opps! Failed to get twitter lists.")))))]
    (reify
      cmd/Command
      (name [this]
        "list")
      (description [this]
        "Show twitter lists.")
      (execute [this {:keys [channel] :as msg} args]
        (let [{:keys [action exit-message ok?] cli-opts :options} (validate-args args)]
          (if exit-message
            (slack.rtm/send-message rtm channel exit-message)
            (condp = action
              :lists (lists-command msg)))))
      Service
      (-get-lists [this]
        (f/if-succ [x (get-lists)]
          x
          (f/wrap x ::failed-to-get-lists)))
      (-get-tweets [this list-id since-id]
        (f/if-succ [x (get-tweets list-id since-id)]
          x
          (f/wrap x ::failed-to-get-tweets))))))

(defmethod ig/init-key :jp.nijohando.chabonze.twitter/list [_ options]
  (service options))
