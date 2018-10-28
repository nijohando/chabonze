(ns jp.nijohando.chabonze.module.bot
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [integrant.core :as ig]
            [duct.core :as core]
            [duct.core.env :as env]
            [duct.core.merge :as merge]
            [jp.nijohando.failable :as f]
            [jp.nijohando.chabonze.bot.util :as util]))

(def ^:private slack-api-token
  (delay (-> (util/require-env "SLACK_API_TOKEN")
             f/ensure)))

(def ^:private store-path
  (delay (f/if-succ [x (util/require-env "STORE_PATH")]
           x
           "store.edn")))

(defn listener-config
  []
  {:jp.nijohando.chabonze.bot/listener
   {:rtm (ig/ref :jp.nijohando.chabonze.bot.slack/rtm)
    :store (ig/ref :jp.nijohando.chabonze.bot/store)
    :logger (ig/ref :duct/logger)
    :commands (ig/refset :jp.nijohando.chabonze/command)}})

(defn slack-rtm-client-config
  []
  ^:demote {:jp.nijohando.chabonze.bot.slack/rtm
            {:api-token (merge/displace @slack-api-token)
             :store (ig/ref :jp.nijohando.chabonze.bot/store)
             :logger (ig/ref :duct/logger)
             :write-timeout (merge/displace 500)
             :ping-interval (merge/displace 60000)
             :bus-buffer-size (merge/displace 64)
             :emitter-buffer-size (merge/displace 32)
             :listener-buffer-size (merge/displace 32)
             :max-connect-retries (merge/displace 5)
             :socket-timeout (merge/displace 1000)
             :connect-timeout (merge/displace 1000)
             :connect-event-timeout (merge/displace 5000)
             :disconnect-event-timeout (merge/displace 5000)}})

(defn- slack-web-client-config
  []
  ^:demote {:jp.nijohando.chabonze.bot.slack/web
            {:api-token (merge/displace @slack-api-token)
             :logger (ig/ref :duct/logger)
             :socket-timeout (merge/displace 1000)
             :connect-timeout (merge/displace 1000)}})

(defn- store-config
  []
  ^:demote {:jp.nijohando.chabonze.bot/store
            {:path (merge/displace @store-path)
             :logger (ig/ref :duct/logger)}})

(defn- apply-bot-module [config options]
  (core/merge-configs config
                      (listener-config)
                      (slack-rtm-client-config)
                      (slack-web-client-config)
                      (store-config)))

(defmethod ig/init-key :jp.nijohando.chabonze.module/bot [_ options]
  #(apply-bot-module % options))
