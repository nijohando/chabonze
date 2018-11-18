(ns jp.nijohando.chabonze.module.twitter-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [clojure.pprint]
            [integrant.core :as ig]
            [duct.core :as core]
            [duct.core.env :refer [env]]
            [jp.nijohando.chabonze.module.twitter]))

(core/load-hierarchy)

(def base-config
  {:jp.nijohando.chabonze.module/twitter {}})

(def override-config
  {:jp.nijohando.chabonze.module/twitter {:command-name "/tw"}
   :duct.profile/base
   {:duct.core/project-ns 'foo
    :jp.nijohando.chabonze.twitter/auth
    {:oauth-consumer-key "aaa"
     :oauth-consumer-secret "bbb"
     :connect-timeout 2000
     :socket-timeout 2000}
    :jp.nijohando.chabonze.twitter/list
    {:connect-timeout 2000
     :socket-timeout 2000}
    :jp.nijohando.chabonze.twitter/search
    {:connect-timeout 2000
     :socket-timeout 2000}}})

(deftest config-default-test
  (is (={:jp.nijohando.chabonze.twitter/search
         {:logger (ig/ref :duct/logger)
          :twauth (ig/ref :jp.nijohando.chabonze.twitter/auth)
          :connect-timeout 1000
          :socket-timeout 1000}
         :jp.nijohando.chabonze.twitter/watch
         {:rtm (ig/ref :jp.nijohando.chabonze.bot.slack/rtm)
          :web (ig/ref :jp.nijohando.chabonze.bot.slack/web)
          :store (ig/ref :jp.nijohando.chabonze.bot/store)
          :logger (ig/ref :duct/logger)
          :twlist (ig/ref :jp.nijohando.chabonze.twitter/list)
          :twsearch (ig/ref :jp.nijohando.chabonze.twitter/search)}
         :jp.nijohando.chabonze.twitter/list
         {:rtm (ig/ref :jp.nijohando.chabonze.bot.slack/rtm)
          :logger (ig/ref :duct/logger)
          :twauth (ig/ref :jp.nijohando.chabonze.twitter/auth)
          :connect-timeout 1000
          :socket-timeout 1000}
         :jp.nijohando.chabonze.twitter/auth
         {:rtm (ig/ref :jp.nijohando.chabonze.bot.slack/rtm)
          :store (ig/ref :jp.nijohando.chabonze.bot/store)
          :logger (ig/ref :duct/logger)
          :oauth-consumer-key (env "TWITTER_OAUTH_CONSUMER_KEY")
          :oauth-consumer-secret (env "TWITTER_OAUTH_CONSUMER_SECRET")
          :connect-timeout 1000
          :socket-timeout 1000}
         :jp.nijohando.chabonze.twitter/command
         {:rtm (ig/ref :jp.nijohando.chabonze.bot.slack/rtm)
          :store (ig/ref :jp.nijohando.chabonze.bot/store)
          :logger (ig/ref :duct/logger)
          :command-name "/twitter"
          :subcommands (ig/refset [:jp.nijohando.chabonze/twitter :jp.nijohando.chabonze/subcommand])}}
        (core/build-config base-config))))

(deftest config-override-test
  (is (= {:duct.core/project-ns 'foo
          :jp.nijohando.chabonze.twitter/search
          {:logger (ig/ref :duct/logger)
           :twauth (ig/ref :jp.nijohando.chabonze.twitter/auth)
           :connect-timeout 2000
           :socket-timeout 2000}
          :jp.nijohando.chabonze.twitter/watch
          {:rtm (ig/ref :jp.nijohando.chabonze.bot.slack/rtm)
           :web (ig/ref :jp.nijohando.chabonze.bot.slack/web)
           :store (ig/ref :jp.nijohando.chabonze.bot/store)
           :logger (ig/ref :duct/logger)
           :twlist (ig/ref :jp.nijohando.chabonze.twitter/list)
           :twsearch (ig/ref :jp.nijohando.chabonze.twitter/search)}
          :jp.nijohando.chabonze.twitter/list
          {:rtm (ig/ref :jp.nijohando.chabonze.bot.slack/rtm)
           :logger (ig/ref :duct/logger)
           :twauth (ig/ref :jp.nijohando.chabonze.twitter/auth)
           :connect-timeout 2000
           :socket-timeout 2000}
          :jp.nijohando.chabonze.twitter/auth
          {:rtm (ig/ref :jp.nijohando.chabonze.bot.slack/rtm)
           :store (ig/ref :jp.nijohando.chabonze.bot/store)
           :logger (ig/ref :duct/logger)
           :oauth-consumer-key "aaa"
           :oauth-consumer-secret "bbb"
           :connect-timeout 2000
           :socket-timeout 2000}
          :jp.nijohando.chabonze.twitter/command
          {:rtm (ig/ref :jp.nijohando.chabonze.bot.slack/rtm)
           :store (ig/ref :jp.nijohando.chabonze.bot/store)
           :logger (ig/ref :duct/logger)
           :command-name "/tw"
           :subcommands (ig/refset [:jp.nijohando.chabonze/twitter :jp.nijohando.chabonze/subcommand])}}
         (core/build-config override-config))))
