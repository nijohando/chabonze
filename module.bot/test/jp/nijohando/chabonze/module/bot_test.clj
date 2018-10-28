(ns jp.nijohando.chabonze.module.bot-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [integrant.core :as ig]
            [duct.core :as core]
            [duct.core.env :as env]
            [jp.nijohando.chabonze.module.bot]))

(core/load-hierarchy)
(derive :duct.logger/fake :duct/logger)

(def base-config
  {:jp.nijohando.chabonze.module/bot {}})

(def override-config
  {:jp.nijohando.chabonze.module/bot {}
   :duct.profile/base
   {:duct.core/project-ns 'foo
    :jp.nijohando.chabonze.bot.slack/rtm
    {:write-timeout 250
     :ping-interval 30000
     :bus-buffer-size 32
     :emitter-buffer-size 16
     :listener-buffer-size 16
     :max-connect-retries 10
     :connect-timeout 3000
     :socket-timeout 2000
     :connect-event-timeout 4000
     :disconnect-event-timeout 3000}
    :jp.nijohando.chabonze.bot.slack/web
    {:socket-timeout 1500
     :connect-timeout 1500}}})

(deftest config-default-test
  (is (= {:jp.nijohando.chabonze.bot/listener
          {:rtm (ig/ref :jp.nijohando.chabonze.bot.slack/rtm)
           :store (ig/ref :jp.nijohando.chabonze.bot/store)
           :logger (ig/ref :duct/logger)
           :commands (ig/refset :jp.nijohando.chabonze/command)}
          :jp.nijohando.chabonze.bot.slack/rtm
          {:api-token (env/env "SLACK_API_TOKEN")
           :store (ig/ref :jp.nijohando.chabonze.bot/store)
           :logger (ig/ref :duct/logger)
           :write-timeout 500
           :ping-interval 60000
           :bus-buffer-size 64
           :emitter-buffer-size 32
           :listener-buffer-size 32
           :max-connect-retries 5
           :socket-timeout 1000
           :connect-timeout 1000
           :connect-event-timeout 5000
           :disconnect-event-timeout 5000}
          :jp.nijohando.chabonze.bot.slack/web
          {:api-token (env/env "SLACK_API_TOKEN")
           :logger (ig/ref :duct/logger)
           :socket-timeout 1000
           :connect-timeout 1000}
          :jp.nijohando.chabonze.bot/store
          {:path "store.edn"
           :logger (ig/ref :duct/logger)}}
         (core/build-config base-config))))

(deftest config-override-test
  (is (= {:duct.core/project-ns 'foo
          :jp.nijohando.chabonze.bot/listener
          {:rtm (ig/ref :jp.nijohando.chabonze.bot.slack/rtm)
           :store (ig/ref :jp.nijohando.chabonze.bot/store)
           :logger (ig/ref :duct/logger)
           :commands (ig/refset :jp.nijohando.chabonze/command)}
          :jp.nijohando.chabonze.bot.slack/rtm
          {:api-token (env/env "SLACK_API_TOKEN")
           :store (ig/ref :jp.nijohando.chabonze.bot/store)
           :logger (ig/ref :duct/logger)
           :write-timeout 250
           :ping-interval 30000
           :bus-buffer-size 32
           :emitter-buffer-size 16
           :listener-buffer-size 16
           :max-connect-retries 10
           :connect-timeout 3000
           :socket-timeout 2000
           :connect-event-timeout 4000
           :disconnect-event-timeout 3000}
          :jp.nijohando.chabonze.bot.slack/web
          {:api-token (env/env "SLACK_API_TOKEN")
           :logger (ig/ref :duct/logger)
           :socket-timeout 1500
           :connect-timeout 1500}
          :jp.nijohando.chabonze.bot/store
          {:path "store.edn"
           :logger (ig/ref :duct/logger)}}
         (core/build-config override-config))))
