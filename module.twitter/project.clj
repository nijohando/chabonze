(defproject jp.nijohando.chabonze/module.twitter "0.1.2"
  :description "Duct module for chabonze twitter"
  :url "https://github.com/nijohando/chabonze/module.twitter"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/tools.cli "0.4.1"]
                 [duct/core "0.7.0-beta1"]
                 [duct/logger "0.2.1"]
                 [integrant "0.7.0"]
                 [fipp "0.6.14"]
                 [ring/ring-codec "1.1.1"]
                 [buddy/buddy-core "1.5.0"]
                 [clj-http "3.9.1"]
                 [cheshire "5.8.0"]
                 [jp.nijohando/failable "0.4.1"]
                 [jp.nijohando/deferable "0.2.1"]
                 [jp.nijohando/event "0.1.5"]
                 [jp.nijohando/event.timer "0.1.1"]
                 [jp.nijohando/ext.async "0.1.0"]
                 [jp.nijohando.chabonze/module.bot "0.1.1"]]
  :plugins [[lein-eftest "0.5.3"]]
  :profiles {:dev {:source-paths   ["dev/src"]
                   :dependencies [[eftest "0.4.1"]]}})
