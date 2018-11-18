(ns jp.nijohando.chabonze.bot.command
  (:refer-clojure :exclude [name])
  (:require
   [clojure.string :as string]
   [jp.nijohando.chabonze.bot.util :as util]))

(defprotocol Command
  (name [this])
  (description [this])
  (execute [this slack-msg args]))

(defn usage
  [title commands]
  (->> ["```"
        title
        ""
        (util/tabular-text
         (array-map :command  "COMMAND" :desc  "DESCRIPTION")
         (->> commands
              (sort-by #(name %))
              (map (fn [cmd] {:command (name cmd) :desc (description cmd)}))))
        "```"]
       (string/join \newline)))

