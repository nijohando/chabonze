(ns jp.nijohando.chabonze.twitter
  (:require
    [jp.nijohando.chabonze.core.slack :as slack]
    [jp.nijohando.chabonze.core.util :as util]
    [jp.nijohando.chabonze.twitter.auth :as auth]
    [jp.nijohando.chabonze.twitter.lists :as lists]
    [jp.nijohando.chabonze.twitter.watch :as watch]
    [jp.nijohando.failable :refer [ftry flet fail guard success? when-failure]]
    [clojure.core.async :as as :refer [sliding-buffer thread close! <!!]]
    [clojure.tools.logging :as log]
    [clojure.string :as string]
    [mount.core :refer [defstate]]
    [environ.core :refer [env]]))

(defn- usage
  []
  (->> ["```"
        "Usage: /twitter <command> [<args>]"
        ""
        (util/tabular-text
          (array-map :command  "COMMAND" :desc  "DESCRIPTION")
          [{:command "auth" :desc "Connect to a twitter account"}
           {:command "lists" :desc "Show twitter's list"}
           {:command "watch" :desc "List, create, or delete watcher task"}
           {:command "help" :desc "Show help"}])
        "```"]
       (string/join \newline)))

(defmulti dispatch-command (fn [slack-msg cmd] (-> cmd first keyword)))
(defmethod dispatch-command :auth [slack-msg cmd]
  (auth/execute slack-msg cmd))

(defmethod dispatch-command :lists [slack-msg cmd]
  (lists/execute slack-msg cmd))

(defmethod dispatch-command :watch [slack-msg cmd]
  (watch/execute slack-msg cmd))

(defmethod dispatch-command :help [slack-msg _]
  (slack/reply-message slack-msg (usage)))

(defmethod dispatch-command :default [slack-msg [command-name & more]]
  (slack/reply-message slack-msg
                       (str "Unknown command: `" command-name "`\n" (usage))))

(defmulti dispatch-message (fn [[mtype mbody]]
                     (cond
                       (not (= mtype :slack)) mtype
                       (slack/command? mbody "/twitter") :command
                       :else :message)))
(defmethod dispatch-message :open [msg])
(defmethod dispatch-message :close [msg])
(defmethod dispatch-message :error [msg])
(defmethod dispatch-message :command [[_ slack-msg]]
  (let [text (:text slack-msg)
        cmd (or (next (string/split text #" "))
                 ["help"])]
    (-> (ftry (dispatch-command slack-msg cmd))
        ((fn [result]
           (when-failure result
             (slack/reply-message
               slack-msg
               (str "Failed to execute twitter command " "`" (first cmd) "`\n"
                    "```"
                    (pr-str result)
                    "```"))))))))

(defmethod dispatch-message :message [[_ slack-msg]]
  (when (= (:ok slack-msg) false)
    (log/error "Got error reply message:" slack-msg)))

(defn run
  [ch]
  (log/info "Start to listen to slack session.")
  (loop []
    (when-let [msg (<!! ch)]
      (dispatch-message msg)
      (recur)))
  (log/info "Stop to listen to slack session."))

(defn start
  []
  (flet [ch (slack/listen (sliding-buffer 128))]
    (thread (run ch))
    (atom {:inbound-ch ch})))

(defn stop
  [{:keys [inbound-ch]}]
  (flet [_ (slack/unlisten inbound-ch)
         _ (close! inbound-ch)]))

(defstate state
          :start (-> (start)
                     (guard "Failed to start"))
          :stop (-> (stop @state)
                    (guard "Failed to stop")))

