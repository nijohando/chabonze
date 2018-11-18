(ns chabonze-app.main
  (:gen-class)
  (:require [duct.core :as duct]))

(duct/load-hierarchy)

(defn -main [& args]
  (let [keys     (or (duct/parse-keys args) [:duct/daemon
                                             :jp.nijohando.chabonze/command
                                             :jp.nijohando.chabonze/subcommand])
        profiles [:duct.profile/prod]]
    (-> (duct/resource "chabonze_app/config.edn")
        (duct/read-config)
        (duct/exec-config profiles keys))))
