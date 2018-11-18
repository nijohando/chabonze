(defproject jp.nijohando.chabonze/module.bot "0.1.0"
  :description "Duct module for chabonze core"
  :url "https://github.com/nijohando/chabonze"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [duct/core "0.7.0-beta1"]
                 [duct/logger "0.2.1"]
                 [integrant "0.7.0"]
                 [fipp "0.6.14"]
                 [diehard "0.7.2"]
                 [clj-http "3.9.1"]
                 [cheshire "5.8.0"]
                 [jp.nijohando/failable "0.4.1"]
                 [jp.nijohando/deferable "0.2.1"]
                 [jp.nijohando/event "0.1.4"]
                 [jp.nijohando/event.websocket "0.1.0"]
                 [jp.nijohando/event.timer "0.1.0"]
                 [jp.nijohando/fs "0.1.0"]
                 [jp.nijohando/ext.async "0.1.0"]]
  :plugins [[lein-eftest "0.5.3"]]
  :profiles {:dev {:source-paths   ["dev/src"]
                   :dependencies [[eftest "0.4.1"]
                                  [clj-http-fake "1.0.3"]
                                  [ring/ring-core "1.7.0"]]}})
