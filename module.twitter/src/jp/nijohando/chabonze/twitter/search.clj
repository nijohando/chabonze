(ns jp.nijohando.chabonze.twitter.search
  (:require
   [clojure.string :as string]
   [integrant.core :as ig]
   [clj-http.client :as http]
   [jp.nijohando.chabonze.twitter.auth :as twitter.auth]
   [jp.nijohando.failable :as f]))

(defprotocol Service
  (-get-tweets [this query since-id]))

(defn get-tweets
  [srv query sice-id]
  (-get-tweets srv query sice-id))

(defn- service
  [{:keys [logger
           twauth
           connect-timeout
           socket-timeout]
    :or {connect-timeout 1000
         socket-timeout 1000}
    :as opts}]
  (letfn [(get-tweets [{:keys [strings lang]} since-id]
            (f/succ->>*
             {:url "https://api.twitter.com/1.1/search/tweets.json"
              :query-params (merge {:q (string/join " " strings)}
                                   (when lang {:lang lang})
                                   (when since-id {:since_id since-id}))
              :method "GET"
              :socket-timeout socket-timeout
              :conn-timeout connect-timeout
              :as :json}
             (twitter.auth/sign twauth)
             http/request
             :body
             :statuses))]
    (reify
      Service
      (-get-tweets [this query since-id]
        (f/if-succ [x (get-tweets query since-id)]
          x
          (f/wrap x ::failed-to-get-tweets))))))

(defmethod ig/init-key :jp.nijohando.chabonze.twitter/search [_ options]
  (service options))
