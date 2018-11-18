(ns jp.nijohando.chabonze.bot.listener
  (:require
   [clojure.core.async :as ca]
   [integrant.core :as ig]
   [jp.nijohando.chabonze.bot.slack.rtm :as slack.rtm]
   [jp.nijohando.chabonze.bot.util :as util]
   [jp.nijohando.chabonze.bot.command :as cmd]
   [jp.nijohando.event :as ev]
   [jp.nijohando.ext.async :as xa]))

(defprotocol Listener
  (-start [this])
  (-stop [this]))

(defn listener
  [{:keys [rtm store logger commands] :as opts}]
  (let [self-id (get-in @store [:slack :self :id])
        event-listener (ca/chan 1)
        cmap (->> commands
                  (map #(vector (cmd/name %) %))
                  (into {}))]
    (letfn [(dispatch-command [{:keys [channel] :as msg} [cname & args]]
              (let [command (get cmap cname)]
                (if (or (nil? command) (= "/help" cname))
                  (if (empty? commands)
                    (slack.rtm/send-message rtm channel "No commands are available.")
                    (slack.rtm/send-message rtm channel (cmd/usage "Following commands are available." commands)))
                  (cmd/execute command msg args))))]
      (reify
        Listener
        (-start [this]
          (ev/listen rtm "/event" event-listener)
          (ca/go-loop []
            (when-some [{msg :value} (xa/<! event-listener)]
              (when-let [cmd (util/parse-slack-command self-id msg)]
                (dispatch-command msg cmd))
              (recur))))
        (-stop [this]
          (xa/close! event-listener))))))

(defmethod ig/init-key :jp.nijohando.chabonze.bot/listener [_ opts]
  (let [lsnr (listener opts)]
    (-start lsnr)
    lsnr))

(defmethod ig/halt-key! :jp.nijohando.chabonze.bot/listener [_ lsnr]
  (-stop lsnr))
