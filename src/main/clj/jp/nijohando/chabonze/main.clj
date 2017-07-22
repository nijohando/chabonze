(ns jp.nijohando.chabonze.main
  (:gen-class)
  (:require
    [jp.nijohando.chabonze.core.slack]
    [jp.nijohando.chabonze.twitter]
    [mount.core :as mount]))

(defn -main
  [& args]
  (mount/start))


