(ns jp.nijohando.chabonze.core.conf
  (:require
    [jp.nijohando.chabonze.core.util :as util]))

(defn slack-api-token
  []
  (util/require-env :chabonze-slack-api-token))



