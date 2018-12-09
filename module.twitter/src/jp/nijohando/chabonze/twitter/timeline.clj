(ns jp.nijohando.chabonze.twitter.timeline
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
  (-get-home-tweets [this since-id]))

(defn get-home-tweets
  [srv sice-id]
  (-get-home-tweets srv sice-id))

(defn- service
  [{:keys [rtm logger twauth connect-timeout socket-timeout]
    :or {connect-timeout 1000
         socket-timeout 1000}
    :as opts}]
  (letfn [(get-home-tweets [since-id]
            (f/succ->>*
             {:url "https://api.twitter.com/1.1/statuses/home_timeline.json"
              :query-params (merge {} (when since-id {:since_id since-id}))
              :method "GET"
              :socket-timeout socket-timeout
              :conn-timeout connect-timeout
              :as :json}
             (twitter.auth/sign twauth)
             http/request
             :body))]
    (reify
      Service
      (-get-home-tweets [this since-id]
        (f/if-succ [x (get-home-tweets since-id)]
          x
          (f/wrap x ::failed-to-get-tweets))))))

(defmethod ig/init-key :jp.nijohando.chabonze.twitter/timeline [_ options]
  (service options))
