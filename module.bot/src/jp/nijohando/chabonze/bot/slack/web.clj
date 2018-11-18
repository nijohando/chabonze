(ns jp.nijohando.chabonze.bot.slack.web
  (:require
   [integrant.core :as ig]
   [clj-http.client :as http]
   [jp.nijohando.chabonze.bot.util :as util]
   [jp.nijohando.failable :as f]))

(defprotocol Client
  (-channels [this])
  (-channel [this id])
  (-post-message [this msg]))

(defn post-message
  [client msg]
  (-post-message client msg))

(defn channels
  [client]
  (-channels client))

(defn channel
  [client id]
  (-channel client id))

(defn find-channel
  [client name]
  (f/if-succ [chs (channels client)]
    (if-some [ch (loop [xs chs]
                      (when-some [[id detail] (first xs)]
                        (if (= name (:name detail))
                          detail
                          (recur (rest xs)))))]
      ch
      (-> (f/fail ::channel-not-found)
          (assoc :name name)))
    (f/wrap chs ::failed-to-find-channel)))

(defn client
  [{:keys [api-token logger socket-timeout connect-timeout]
    :or {socket-timeout 1000
         connect-timeout 1000}
    :as options}]
  (letfn [(get-channel [id]
            (f/succ->* (http/post "https://slack.com/api/channels.info"
                                  {:form-params {:token api-token
                                                 :channel id}
                                   :socket-timeout socket-timeout
                                   :conn-timeout connect-timeout
                                   :as :json})
                       :body))
          (get-channels []
            (f/succ->* (http/post "https://slack.com/api/channels.list"
                                  {:form-params {:token api-token}
                                   :socket-timeout socket-timeout
                                   :conn-timeout connect-timeout
                                   :as :json})
                       :body))
          (post-message [params]
            (f/succ->* (http/post "https://slack.com/api/chat.postMessage"
                                  {:form-params (merge {:token api-token} params)
                                   :socket-timeout socket-timeout
                                   :conn-timeout connect-timeout
                                   :as :json})
                       :body))]
    (reify
      Client
      (-post-message [this params]
        (let [{:keys [ok error channel] :as x} (post-message params)]
          (if (and (f/succ? x) (true? ok))
            x
            (if (f/fail? x)
              (f/wrap x ::failed-to-post-message)
              (-> (f/fail ::failed-to-post-message)
                  (assoc :error error))))))
      (-channel [this id]
        (let [{:keys [ok error channel] :as x} (get-channel id)]
          (if (and (f/succ? x) (true? ok))
            channel
            (if (f/fail? x)
              (f/wrap x ::failed-to-get-channel)
              (-> (f/fail ::failed-to-get-channel)
                  (assoc :error error))))))
      (-channels [this]
        (let [{:keys [ok error channels] :as x} (get-channels)]
          (if (and (f/succ? x) (true? ok))
            (->> channels
                 (map #(vector (:id %) %))
                 (into {}))
            (if (f/fail? x)
              (f/wrap x ::failed-to-get-channels)
              (-> (f/fail ::failed-to-get-channels)
                  (assoc :error error)))))))))

(defmethod ig/init-key :jp.nijohando.chabonze.bot.slack/web [_ options]
  (client options))
