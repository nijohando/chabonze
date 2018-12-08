(ns jp.nijohando.chabonze.bot.slack.rtm
  (:require
   [clojure.core.async :as ca]
   [integrant.core :as ig]
   [duct.logger :as dl]
   [clj-http.client :as http]
   [cheshire.core :as json]
   [diehard.core :as dh]
   [jp.nijohando.failable :as f]
   [jp.nijohando.deferable :as d]
   [jp.nijohando.event :as ev]
   [jp.nijohando.event.protocols :as evp]
   [jp.nijohando.event.websocket :as ws]
   [jp.nijohando.event.timer :as tm]
   [jp.nijohando.ext.async :as xa]
   [jp.nijohando.chabonze.bot.store :as st]
   [jp.nijohando.chabonze.bot.util :as util]))

(defprotocol Client
  (-connect! [this])
  (-disconnect! [this immediate?])
  (-send! [this slack-event]))

(defn connect!
  [client]
  (-connect! client))

(defn disconnect!
  ([client]
   (disconnect! client false))
  ([client immediate?]
   (-disconnect! client immediate?)))

(defn send-message
  [client channel-id text]
  (-send! client {:type "message"
                  :channel channel-id
                  :text text}))

(defn send-typing
  [client channel-id]
  (-send! client {:type "typing"
                  :channel channel-id}))

(defn client
  [{:keys [api-token
           store
           logger
           write-timeout
           ping-interval
           bus-buffer-size
           emitter-buffer-size
           listener-buffer-size
           max-connect-retries
           connect-retry-backoff-initial-delay
           connect-retry-backoff-max-delay
           connect-timeout
           socket-timeout
           connect-event-timeout
           disconnect-event-timeout]
    :or {write-timeout 300
         ping-interval 60000
         bus-buffer-size 256
         emitter-buffer-size 32
         listener-buffer-size 32
         max-connect-retries 5
         connect-retry-backoff-initial-delay 1000
         connect-retry-backoff-max-delay 60000
         connect-event-timeout 3000
         disconnect-event-timeout 3000} :as opts}]
  (let [ws-emitter (ca/chan emitter-buffer-size)
        ws-listener (ca/chan listener-buffer-size)
        ws-bus (ws/client)
        cl-emitter (ca/chan emitter-buffer-size)
        cl-bus (ev/blocking-bus bus-buffer-size)
        tm-listener (ca/chan 1)
        timer (tm/timer)
        msg-id (atom 0)]
    (letfn [(initiate-rtm-session []
              (f/succ->* (http/get "https://slack.com/api/rtm.connect"
                                    {:query-params {:token api-token}
                                     :socket-timeout socket-timeout
                                     :conn-timeout connect-timeout
                                     :as :json})
                          :body))
            (next-msg-id []
              (swap! msg-id inc))
            (reply-message? [slack-event]
              (every? #(contains? slack-event %) [:ok :reply_to]))
            (send-event [slack-event]
              (let [msg-id (next-msg-id)
                    ws-event (->> (assoc slack-event :id msg-id )
                                  json/generate-string
                                  (ev/event "/send/text"))]
                (f/if-succ [x (xa/>!! ws-emitter ws-event :timeout write-timeout)]
                  msg-id
                  (f/wrap x :write-text-message-error))))
            (on-slack-connect [msg]
              (dl/log logger :info :connected-to-slack)
              (reset! msg-id 0)
              (tm/repeat! timer ping-interval ping-interval (ev/event "/ping"))
              (f/when-fail [x (xa/>!! cl-emitter (ev/event "/connect") :timeout write-timeout)]
                (dl/log logger :error :write-slack-connect-event-error x)))
            (on-slack-disconnect []
              (dl/log logger :info :disconnected-from-slack)
              (f/when-fail [x (xa/>!! cl-emitter (ev/event "/disconnect") :timeout write-timeout)]
                (dl/log logger :error :write-slack-disconnect-event-error x)))
            (on-slack-reconnect-url [msg]
              (let [url (:url msg)]
                (dl/log logger :debug :update-slack-reconnect-url {:url url})
                (st/transact! assoc-in [:slack :reconnect_url] url)))
            (propagate-slack-event [{:keys [reply_to] :as msg}]
              (let [path (if (reply-message? msg)
                           (format "/reply/%d", reply_to)
                           "/event")]
                (f/when-fail [x (xa/>!! cl-emitter (ev/event path msg) :timeout write-timeout)]
                  (dl/log logger :error :write-slalck-event-error x))))
            (handle-slack-message [msg]
              (dl/log logger :debug :handle-slack-message msg)
              (condp = (:type msg)
                "hello"         (on-slack-connect msg)
                "reconnect_url" (on-slack-reconnect-url msg)
                nil)
              (propagate-slack-event msg))
            (on-ws-connect [msg]
              (dl/log logger :debug :connected-via-websocket))
            (on-ws-connect-failed [msg]
              (dl/log logger :error :websocket-connect-failed))
            (on-ws-disconnect [{:keys [value]}]
              (dl/log logger :debug :disconnected-from-websocket value)
              (tm/cancel-all! timer)
              (on-slack-disconnect)
              (when (not= 1000 (:code value))
                (f/if-fail [x (connect!)]
                  (dl/log logger :error :reconnect-failed))))
            (on-ws-disconnect-failed [{:keys [value]}]
              (dl/log logger :warn :disconnected-from-slack-with-error value))
            (on-ws-message-text [{:keys [value]}]
              (-> value
                  (json/parse-string true)
                  handle-slack-message))
            (on-ws-error [{:keys [value]}]
              (dl/log logger :error :got-error value)
              (xa/>!! cl-emitter (ev/event "/error" value) :timeout write-timeout))
            (handle-websocket-message [msg]
              (condp = (:path msg)
                "/connect"           (on-ws-connect msg)
                "/connect-failed"    (on-ws-connect-failed msg)
                "/disconnect"        (on-ws-disconnect msg)
                "/disconnect-failed" (on-ws-disconnect-failed msg)
                "/message/text"      (on-ws-message-text msg)
                "/error"             (on-ws-error msg)
                nil))
            (connect!*
              ([]
               (dl/log logger :debug :initiate-rtm-session)
               (let [{:keys [ok self url error] :as x} (initiate-rtm-session)]
                 (if (and (f/succ? x) (true? ok))
                   (do
                     (st/transact! store assoc-in [:slack :self] self)
                     (connect!* url))
                   (if (f/fail? x)
                     (f/wrap x ::initiate-rtm-session-error)
                     (-> (f/fail ::initiate-rtm-session-error)
                         (assoc :error error))))))
              ([url]
               (dl/log logger :debug :connecting-via-websocket url)
               (f/do* (ws/connect! ws-bus url))))
            (disconnect! [immediate?]
              (when (and (ws/disconnect! ws-bus) (not immediate? ))
                (d/do*
                  (let [listener (ca/chan 1)
                        _ (d/defer (ca/close! listener))]
                    (ev/listen cl-bus ["/" ["disconnect"] ["disconnect-failed"]] listener)
                    (f/when-fail [x (xa/<!! listener :timeout disconnect-event-timeout)]
                      (dl/log logger :warn :disconnect-event-timeout x))))))
            (connect! []
              (dh/with-retry {:retry-if (fn [val ex] (or (f/fail? val) (some? ex)))
                              :max-retries max-connect-retries
                              :backoff-ms [connect-retry-backoff-initial-delay connect-retry-backoff-max-delay]}
                (d/do*
                  (let [listener (ca/chan 1)
                        _ (d/defer (ca/close! listener))
                        fail #(f/wrap % ::connect-failed)]
                    (ev/listen ws-bus ["/" ["connect"] ["connect-failed"]] listener)
                    (when (connect!*)
                      (f/if-succ [x (xa/<!! listener :timeout connect-event-timeout)]
                        (when (= "/connect-failed" (:path x))
                          (fail (:value x)))
                        (fail x)))))))]
      (ev/emitize cl-bus cl-emitter)
      (ev/emitize ws-bus ws-emitter)
      (ev/listen ws-bus "/*" ws-listener)
      (ca/go-loop []
        (when-some [x (xa/<! ws-listener)]
          (f/if-fail [x (handle-websocket-message x)]
            (dl/log logger :error :handle-websocket-message-error))
          (recur)))
      (dl/log logger :info :start-connection-keeper)
      (ev/listen timer "/ping" tm-listener)
      (ca/go-loop []
        (let [x (xa/<! tm-listener)]
          (if (some? x)
            (do (dl/log logger :debug :ping)
                (f/if-fail [x (send-event {:type "ping"})]
                  (dl/log logger :error :send-ping-error x))
                (recur))
            (dl/log logger :info :stop-connetion-keeper))))
      (reify
        Client
        (-connect! [this]
          (connect!))
        (-disconnect! [this immediate?]
          (disconnect! immediate?))
        (-send! [this event]
          (send-event event))
        evp/Emittable
        (emitize [_ emitter-ch reply-ch]
          (ev/emitize cl-bus emitter-ch reply-ch))
        evp/Listenable
        (listen [_ routes listener-ch]
          (ev/listen cl-bus routes listener-ch))
        evp/Closable
        (close! [this]
          (disconnect! false)
          (xa/close! tm-listener cl-emitter ws-emitter ws-listener)
          (ev/close! timer cl-bus ws-bus))))))

(defmethod ig/init-key :jp.nijohando.chabonze.bot.slack/rtm [_ opts]
  (let [c (client opts)]
    (connect! c)
    c))

(defmethod ig/halt-key! :jp.nijohando.chabonze.bot.slack/rtm [_ client]
  (ev/close! client))
