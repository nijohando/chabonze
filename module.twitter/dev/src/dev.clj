(ns dev
  (:refer-clojure :exclude [test])
  (:require [clojure.repl :refer :all]
            [fipp.edn :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [eftest.runner :as eftest]
            [duct.core.env :refer [env]]
            [jp.nijohando.failable :as f]
            [jp.nijohando.chabonze.twitter.auth :as auth]
            [jp.nijohando.chabonze.twitter.mock :as mock]))

(def store (mock/store))
(def logger (mock/logger))

(defn test []
  (eftest/run-tests (eftest/find-tests "test")))

(clojure.tools.namespace.repl/set-refresh-dirs "dev/src" "src" "test")
