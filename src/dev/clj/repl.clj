(ns repl
  (:require [jp.nijohando.chabonze.core.slack :as slack]
            [jp.nijohando.chabonze.twitter :as twitter]
            [mount.core :as mount]
            [clojure.tools.namespace.repl :as tn]))

(defn start
  []
  (mount/start))

(defn stop
  []
  (mount/stop))

(defn refresh
  []
  (stop)
  (tn/refresh))

(defn refresh-all
  []
  (stop)
  (tn/refresh-all))

(defn go
  "starts all states defined by defstate"
  []
  (start) :ready)

(defn reset
  "stops all states defined by defstate, reloads modified source files, and restarts the states"
  []
  (stop)
  (tn/refresh :after 'repl/go))

(mount/in-clj-mode)
