(ns jp.nijohando.chabonze.twitter.command
  (:require
   [clojure.string :as string]
   [clojure.core.async :as ca]
   [integrant.core :as ig]
   [jp.nijohando.chabonze.bot.slack.rtm :as slack.rtm]
   [jp.nijohando.chabonze.bot.util :as util]
   [jp.nijohando.chabonze.bot.command :as cmd]
   [jp.nijohando.failable :as f]
   [jp.nijohando.event :as ev]
   [jp.nijohando.ext.async :as xa]))

(defn command
  [{:keys [rtm store logger command-name subcommands] :as opts}]
  (let [self-id (get-in @store [:slack :self :id])
        listener (ca/chan 1)
        cmap (->> subcommands
                  (map #(vector (cmd/name %) %))
                  (into {}))]
    (letfn [(dispatch-command [{:keys [channel] :as msg} [cname & args]]
              (if-let [command (get cmap cname)]
                (cmd/execute command msg args)
                (slack.rtm/send-message rtm channel (cmd/usage (str "Usage: " command-name " <command> [<args>]" ) subcommands))))]
      (reify
        cmd/Command
        (name [this]
          command-name)
        (description [this]
          "Twitter integration.")
        (execute [this slack-msg args]
          (dispatch-command slack-msg args))))))

(defmethod ig/init-key :jp.nijohando.chabonze.twitter/command [_ opts]
  (command opts))
