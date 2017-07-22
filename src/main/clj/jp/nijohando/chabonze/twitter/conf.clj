(ns jp.nijohando.chabonze.twitter.conf
  (:require
    [jp.nijohando.chabonze.core.util :as util]))

(defn oauth-consumer-key
  []
  (util/require-env :chabonze-twitter-consumer-key))

(defn oauth-consumer-secret
  []
  (util/require-env :chabonze-twitter-consumer-secret))

