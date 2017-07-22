(ns jp.nijohando.chabonze.core.slack
  (:require
    [jp.nijohando.chabonze.core.websocket :as ws]
    [jp.nijohando.chabonze.core.store :as store]
    [jp.nijohando.chabonze.core.conf :as conf]
    [jp.nijohando.failable :refer [flet ftry fail guard f->> when-failure]]
    [clojure.core.async :as as :refer [chan <!! alts!! alt!! timeout sliding-buffer thread]]
    [clojure.string :as string]
    [clojure.tools.logging :as log]
    [mount.core :refer [defstate]]
    [clj-http.client :as http]
    [cheshire.core :refer [parse-string generate-string]]))

(declare ping)

(defn- initiate-rtm-session
  []
  (flet [token (conf/slack-api-token)
         res (http/get "https://slack.com/api/rtm.start"
                         {:query-params {:token token}
                          :as :json})
         body (:body res)
         _ (or (:ok body)
               (fail "Failed to initiate RTM API session"))
         self (-> (:self body)
                  (select-keys [:id :name]))
         url (:url body)]
    (log/info "Succeeded to initiate RTM session. " self)
    {:self self
     :url url}))

(defn- slack-msg
  [text-msg]
  (if-let [text (:text text-msg)]
    (let [id (store/get-in [:slack :self :id])
          kwd (str "<@" id ">")]
      (if-let [idx (string/index-of text kwd)]
        (merge text-msg {:mentioned? true :text (-> text
                                                    (string/replace kwd "")
                                                    (string/trim))})
        text-msg))
    text-msg))

(defn- convert-message
  [msg]
  (if-let [txt-msg (ws/message-data-text-body msg)]
    [:slack (slack-msg txt-msg)]
    msg))

(defprotocol Client
  (start [this])
  (stop [this])
  (listen [this buffer-or-n xform])
  (unlisten [this ch])
  (send* [this msg]))

(defn new-client
  []
  (let [connection (atom nil)
        event-id-seq (atom 0)]
    (letfn [(connect [url]
              (reset! event-id-seq 0)
              (f->> (ws/connect url)
                    (reset! connection)))
            (disconnect []
              (when-let [conn @connection]
                (ws/disconnect conn)))
            (reconnect []
              (when-some [url (store/get-in [:slack :reconnect_url])]
                (log/info "reconnect url:" url)
                (connect url)))
            (handle-reconnection-url [msg]
              (when-let [{rtm-type :type url :url} (ws/message-data-text-body msg)]
                (when (= rtm-type "reconnect_url")
                  (store/transact! #(assoc-in % [:slack :reconnect_url] url))))
              msg)
            (handle-disconnection [msg]
              (when-let [[wsm-type body] msg]
                (when (= wsm-type :close)
                  (let [reason-code (:code body)]
                    (if (not= reason-code 1000)
                        (when-failure (reconnect)
                          (log/error "Failed to reconnect."))))))
              msg)
            (watch [conn]
              (flet [ws-ch (ws/listen conn (chan 64))]
                (thread
                  (loop []
                    (when-some [msg (alt!! ws-ch ([msg] msg) (timeout 60000) :timeout)]
                      (if (= msg :timeout)
                        (ping)
                        (-> msg
                            handle-reconnection-url
                            handle-disconnection))
                      (recur))))
                conn))
            (next-event-id []
              (swap! event-id-seq inc))]
      (reify Client
        (start [this]
          (flet [{:keys [self url]} (initiate-rtm-session)
                 _ (store/transact! #(assoc-in % [:slack :self] self))
                 _ (f->> (connect url)
                         (watch))]
            this))
        (stop [this]
          (flet [_ (disconnect)]
            this))
        (listen [this buffer-or-n xform]
          (if-let [conn @connection]
            (ws/listen conn (chan buffer-or-n xform))
            (fail "No connection established")))
        (unlisten [this ch]
          (when-let [conn @connection]
            (ws/unlisten conn ch)))
        (send* [this msg]
          (when-let [conn @connection]
            (ws/sendText conn (generate-string (assoc msg :id (next-event-id))))))))))

(defn start-client
  []
  (flet [client (new-client)]
    (.start client)))

(defn stop-client
  [client] 
  (.stop client))

(defstate client
          :start (-> (start-client)
                     (guard "Failed to start socket"))
          :stop (-> (stop-client client)
                    (guard "Failed to stop socket")))

(defn listen
  [buffer-or-n]
   (.listen client buffer-or-n (map convert-message)))

(defn unlisten
  [ch]
  (.unlisten client ch))


(defn command?
  [{:keys [mentioned? text] :as slack-msg} command-name]
  (and
    mentioned?
    (not (string/blank? text))
    (string/starts-with? text command-name)))

(defn reply-message
  [slack-msg text]
  (let [msg {:type "message"
             :channel (:channel slack-msg)
             :text text}]
    (.send* client msg)))

(defn send-message
  [channel text]
  (let [msg {:type "message"
             :channel channel
             :text text}]
    (.send* client msg)))

(defn post-message
  [msg]
  (ftry (http/post "https://slack.com/api/chat.postMessage"
                   {:form-params (merge {:token (conf/slack-api-token)} msg)})))
(defn typing
  [slack-msg]
  (let [msg {:type "typing"
             :channel (:channel slack-msg)}]
    (.send* client msg)))

(defn ping
  []
  (let [msg {:type "ping"}]
    (.send* client msg)))

(defn channels
  []
  (flet [res (http/post "https://slack.com/api/channels.list"
                        {:form-params {:token (conf/slack-api-token)}
                         :as :json})
         chs (map (fn [x] [(:id x) x]) (-> res :body :channels))]
    (into {} chs)))
