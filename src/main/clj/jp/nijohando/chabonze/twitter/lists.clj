(ns jp.nijohando.chabonze.twitter.lists
  (:require
    [jp.nijohando.chabonze.core.slack :as slack]
    [jp.nijohando.chabonze.core.store :as store]
    [jp.nijohando.chabonze.core.util :as util]
    [jp.nijohando.chabonze.twitter.auth :refer [sign]]
    [jp.nijohando.failable :refer [ftry flet f->> f-> fail guard]]
    [clojure.core.memoize :as memo]
    [clojure.tools.logging :as log]
    [clojure.string :as string]
    [clojure.tools.cli :refer [parse-opts]]
    [clj-http.client :as http]))

(defn invoke-api-list*
  []
  (flet [req (sign {:url "https://api.twitter.com/1.1/lists/list.json"
                    :method "GET"
                    :as :json})]
    (http/request req)))

(def invoke-api-list (memo/ttl invoke-api-list* {} :ttl/threshold 60000))

(defn exists?
  [slug]
  (flet [{:keys [body]} (invoke-api-list)
         slugs (->> body 
                    (map #(:slug %))
                    (into #{}))]
    (contains? slugs slug)))

(defn- perform-list
  [slack-msg]
  (flet [_ (slack/typing slack-msg)
         response (invoke-api-list)
         result (->> response
                     :body
                     (map #(select-keys % [:slug :name :mode :member_count])))
         text (str "```" (util/tabular-text (array-map :slug "SLUG" :mode "MODE" :member_count "MEMBERS" :name "NAME") result) "```")]
    (slack/reply-message slack-msg text)))

(def ^:private command-options
  [["-h" "--help"]])

(defn- usage
  [option-summary]
  (->> ["```"
        "Usage: /twitter lists"
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
      errors {:exit-message (str (error-msg errors) "\n\n" (usage summary))}
      :else {:action :list})))

(defn execute
  [slack-msg [_ & args]]
  (flet [{:keys [action options exit-message ok?]} (validate-args args)]
    (if exit-message
      (slack/reply-message slack-msg exit-message)
      (condp = action
        :list (perform-list slack-msg)))))

