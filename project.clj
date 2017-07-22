(defproject jp.nijohando/chabonze "1.0.0"
  :description "Bot for nijohando"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/core.async "0.3.442"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/core.memoize "0.5.8"]
                 [ch.qos.logback/logback-classic "1.2.2"]
                 [clj-http "3.6.1"]
                 [cheshire "5.7.0"]
                 [mount "0.1.11"]
                 [environ "1.1.0"]
                 [javax.websocket/javax.websocket-api "1.0"]
                 [org.eclipse.jetty.websocket/javax-websocket-client-impl "9.4.2.v20170220"]
                 [ring/ring-codec "1.0.1"]
                 [buddy/buddy-core "1.2.0"]
                 [jp.nijohando/failable "0.1.1"]
                 [jp.nijohando/deferable "0.1.0"]]
  :exclusions [[org.clojure/clojurescript]]
  :plugins [[lein-cljfmt "0.5.6"]]
  :source-paths ["src/main/clj"]
  :java-source-paths ["src/main/java"]
  :resource-paths ["src/main/resources"]
  :aot [jp.nijohando.chabonze.main]
  :main jp.nijohando.chabonze.main
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.3.0-alpha3"]]
                   :source-paths ["src/dev/clj"]
                   :resource-paths ["src/dev/resources"]
                   :repl-options {:init-ns repl}}
             :uberjar {:resource-paths ["src/prod/resources"]
                       :uberjar-name "chabonze-standalone.jar"}})
