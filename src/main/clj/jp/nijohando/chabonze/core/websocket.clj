(ns jp.nijohando.chabonze.core.websocket
  (:require
    [jp.nijohando.failable :refer [ftry flet fail guard]]
    [clojure.tools.logging :as log]
    [clojure.core.async :as as :refer [chan go >! mult tap untap buffer sliding-buffer close!]]
    [mount.core :refer [defstate]]
    [cheshire.core :refer [parse-string]])
  (:import
    (jp.nijohando.chabonze.websocket StringMessageHandler)
    (java.net URI)
    (javax.websocket ContainerProvider
                     WebSocketContainer
                     Endpoint
                     Session
                     EndpointConfig
                     CloseReason
                     ClientEndpointConfig$Builder)))

(defprotocol Connection
  (on-open [this session])
  (on-close [this reason])
  (on-error [this failure])
  (listen [this ch])
  (unlisten [this ch])
  (close [this])
  (sendText [this msg]))

(defn- init-container
  []
  (ftry (ContainerProvider/getWebSocketContainer)))

(defstate container :start (-> (init-container)
                               (guard "Failed to initialize websocket container")))

(defn- new-connection
  [{:keys [inbound-buffer outbound-buffer] :as opts}]
  (flet [inbound-chan (chan inbound-buffer)
         outbound-chan (chan outbound-buffer)
         inbound-mult (delay (mult inbound-chan))
         current-session (atom nil)]
    (reify
      Connection
      (on-open [this session]
        (log/debug "Session is opend.")
        (.addMessageHandler session this)
        (reset! current-session session)
        (go (>! inbound-chan [:open])))
      (on-close [_ reason]
        (log/debug "Session is closed.")
        (reset! current-session nil)
        (go (>! inbound-chan [:close reason])
            (close! inbound-chan)))
      (on-error [_ failure]
        (log/debug "Got an error: " failure)
        (go (>! inbound-chan [:error failure])))
      (listen [_ ch]
        (tap @inbound-mult ch))
      (unlisten [_ ch]
        (untap @inbound-mult ch))
      (close [_]
        (when current-session
          (.close @current-session)))
      (sendText [_ msg]
        (when current-session
          (.sendText (.getBasicRemote @current-session) msg)))

      StringMessageHandler
      (onMessage [_ msg]
        (go (>! inbound-chan [:data [:text (parse-string msg true)]]))))))

(defn- endpoint
  [conn]
  (proxy [Endpoint] []
    (onOpen [^Session session ^EndpointConfig config]
      (.on-open conn session))
    (onClose [^Session session ^CloseReason close-reason]
      (let [reason {:code (.. close-reason getCloseCode getCode)
                    :phrase (.. close-reason getReasonPhrase)}]
        (.on-close conn reason)))
    (onError [^Session session ^Throwable th]
      (.on-error conn (fail th)))))

(defn connect
  ([url]
   (connect url {:inbound-buffer (sliding-buffer 128)
                 :outbound-buffer (buffer 128)}))
  ([url opts]
   (flet [conn (new-connection opts)
          ep (endpoint conn)
          config (-> (ClientEndpointConfig$Builder/create)
                     (.build))
          uri (URI/create url)
          _ (.connectToServer container ep config uri)]
     conn)))

(defn disconnect
  [conn]
  (ftry (.close conn))
  nil)

(defn listen
  [conn ch]
  (.listen conn ch))

(defn unlisten
  [conn ch]
  (.unlisten conn ch))

(defn sendText
  [conn text]
  (.sendText conn text))

(defn message-type
  [msg]
  (first msg))

(defn message-body
  [msg]
  (second msg))

(defn message-data-body
  [msg]
  (when (= (message-type msg) :data)
    (message-body msg)))

(defn message-data-text-body
  [msg]
  (when-let [[data-type body] (message-data-body msg)]
    (when (= data-type :text)
      body)))
