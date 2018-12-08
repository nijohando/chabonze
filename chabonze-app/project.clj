(defproject chabonze-app "0.2.0"
  :description "Clojure slack bot"
  :url "https://github.com/nijohando/chabonze"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.0-beta4"]
                 [duct/core "0.7.0-beta1"]
                 [fipp "0.6.14"]
                 [duct/module.logging "0.4.0-alpha1"]
                 [jp.nijohando.chabonze/module.bot "0.1.1"]
                 ;[jp.nijohando.chabonze/module.twitter "0.1.1"]
                 ]
  :plugins [[duct/lein-duct "0.11.0-beta1"]]
  :main ^:skip-aot chabonze-app.main
  :uberjar-name "chabonze.jar"
  :resource-paths ["resources" "target/resources"]
  :prep-tasks     ["javac" "compile" ["run" ":duct/compiler"]]
  :profiles
  {:dev  [:project/dev :profiles/dev]
   :repl {:prep-tasks   ^:replace ["javac" "compile"]
          :repl-options {:init-ns user}}
   :uberjar {:aot :all}
   :profiles/dev {}
   :project/dev  {:source-paths   ["dev/src"]
                  :resource-paths ["dev/resources"]
                  :dependencies   [[integrant/repl "0.3.1"]
                                   [eftest "0.5.3"]]}})
