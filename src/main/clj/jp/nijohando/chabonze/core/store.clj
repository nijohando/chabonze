(ns jp.nijohando.chabonze.core.store
  (:refer-clojure :exclude [load load-file get-in])
  (:require
    [jp.nijohando.failable :refer [ftry flet fail guard]]
    [clojure.java.io :as jio]
    [clojure.edn :as edn]
    [environ.core :refer [env]]
    [mount.core :refer [defstate]])
  (:import
    (clojure.java.io IOFactory)
    (java.io File
             FileInputStream
             FileOutputStream)
    (java.nio.file Files
                   Paths
                   Path
                   LinkOption)))

(def empty-strings (into-array String []))
(def empty-link-options (into-array LinkOption []))

(defmulti ^:private ^Path path class)
(defmethod path Path [^Path p]
  p)
(defmethod path String [^String s]
  (path (. Paths get s empty-strings)))
(defmethod path File [^File file]
  (path (. file toPath)))
(defmethod path nil [_]
  nil)

(extend Path
  jio/IOFactory
  (assoc jio/default-streams-impl
         :make-input-stream (fn [^Path x opts] (jio/make-input-stream (FileInputStream. (. x toFile)) opts))
         :make-output-stream (fn [^Path x opts] (jio/make-output-stream (FileOutputStream. (. x toFile) (boolean (:append opts))) opts))))

(defn- exists?
  [x]
  (Files/exists (path x) empty-link-options))

(defn- load-file
  [file-path]
  (flet [p (path file-path)]
    (if (exists? p)
      (-> (slurp p)
          (edn/read-string))
      {})))

(defn- save-file
  [file-path data]
  (flet [edn (pr-str data)]
    (spit file-path edn)))

(defn- config-store-path
  []
  (or (-> (not-empty (env :chabonze-store-path))
          path)
      (fail "CHABONZE_STORE_PATH not set")))

(defprotocol Store
  (start [this])
  (stop [this])
  (get-state [this])
  (transact! [this f]))

(defn new-store
  []
  (flet [file-path (agent nil)
         state (ref {})]
    (reify Store
      (start [this]
        (flet [p (config-store-path)
               _ (send file-path (fn [_] p))
               _ (await file-path)
               data (load-file p)]
          (dosync
            (ref-set state data))
          this))
      (stop [this]
        this)
      (get-state [this]
        @state)
      (transact! [this f]
        (dosync
          (flet [new-state (alter state f)]
            (send-off file-path (fn [p] (-> (save-file p new-state)
                                            (guard "Failed to save store")) p))))))))

(defn start-store
  []
  (flet [store (new-store)]
    (.start store)))

(defn stop-store
  [store]
  (.stop store))

(defstate store*
          :start (-> (start-store)
                     (guard "Failed to start store"))
          :stop (-> (stop-store store*)
                    (guard "Failed to stop store")))

(defn transact!
  [f]
  (.transact! store* f))

(defn get-state
  []
  (.get-state store*))

(defn get-in
  [ks]
  (clojure.core/get-in (get-state) ks))
