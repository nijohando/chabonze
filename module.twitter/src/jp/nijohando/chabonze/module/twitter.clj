(ns jp.nijohando.chabonze.module.twitter
  (:require [integrant.core :as ig]
            [duct.core :as core]
            [duct.core.env :as env]
            [duct.core.merge :as merge]
            [jp.nijohando.failable :as f]
            [jp.nijohando.chabonze.bot.util :as util]))

(def ^:private oauth-consumer-key
  (delay (-> (util/require-env "TWITTER_OAUTH_CONSUMER_KEY")
             f/ensure)))

(def ^:private oauth-consumer-secret
  (delay (-> (util/require-env "TWITTER_OAUTH_CONSUMER_SECRET")
             f/ensure)))

(defn- search-config
  []
  ^:demote {:jp.nijohando.chabonze.twitter/search
            {:logger (ig/ref :duct/logger)
             :twauth (ig/ref :jp.nijohando.chabonze.twitter/auth)
             :socket-timeout (merge/displace 1000)
             :connect-timeout (merge/displace 1000)}})

(defn- auth-config
  []
  ^:demote {:jp.nijohando.chabonze.twitter/auth
            {:rtm (ig/ref :jp.nijohando.chabonze.bot.slack/rtm)
             :store (ig/ref :jp.nijohando.chabonze.bot/store)
             :logger (ig/ref :duct/logger)
             :socket-timeout (merge/displace 1000)
             :connect-timeout (merge/displace 1000)
             :oauth-consumer-key (merge/displace @oauth-consumer-key)
             :oauth-consumer-secret (merge/displace @oauth-consumer-secret)}})
(defn- list-config
  []
  ^:demote {:jp.nijohando.chabonze.twitter/list
            {:rtm (ig/ref :jp.nijohando.chabonze.bot.slack/rtm)
             :logger (ig/ref :duct/logger)
             :twauth (ig/ref :jp.nijohando.chabonze.twitter/auth)
             :socket-timeout (merge/displace 1000)
             :connect-timeout (merge/displace 1000)}})

(defn- timeline-config
  []
  ^:demote {:jp.nijohando.chabonze.twitter/timeline
            {:rtm (ig/ref :jp.nijohando.chabonze.bot.slack/rtm)
             :logger (ig/ref :duct/logger)
             :twauth (ig/ref :jp.nijohando.chabonze.twitter/auth)
             :socket-timeout (merge/displace 1000)
             :connect-timeout (merge/displace 1000)}})

(defn- watch-config
  []
  ^:demote {:jp.nijohando.chabonze.twitter/watch
            {:rtm (ig/ref :jp.nijohando.chabonze.bot.slack/rtm)
             :web (ig/ref :jp.nijohando.chabonze.bot.slack/web)
             :store (ig/ref :jp.nijohando.chabonze.bot/store)
             :logger (ig/ref :duct/logger)
             :twlist (ig/ref :jp.nijohando.chabonze.twitter/list)
             :twsearch (ig/ref :jp.nijohando.chabonze.twitter/search)
             :twtimeline (ig/ref :jp.nijohando.chabonze.twitter/timeline)}})

(defn- command-config
  [command-name]
  ^:demote {:jp.nijohando.chabonze.twitter/command
            {:rtm (ig/ref :jp.nijohando.chabonze.bot.slack/rtm)
             :store (ig/ref :jp.nijohando.chabonze.bot/store)
             :command-name (merge/displace command-name)
             :subcommands (ig/refset [:jp.nijohando.chabonze/twitter :jp.nijohando.chabonze/subcommand])
             :logger (ig/ref :duct/logger)}})

(defn- apply-twitter-module [config {:keys [command-name] :or {command-name "/twitter"}}]
  (core/merge-configs config
                      (search-config)
                      (watch-config)
                      (list-config)
                      (timeline-config)
                      (auth-config)
                      (command-config command-name)))

(defmethod ig/init-key :jp.nijohando.chabonze.module/twitter [_ options]
  #(apply-twitter-module % options))
