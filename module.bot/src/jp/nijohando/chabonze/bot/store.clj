(ns jp.nijohando.chabonze.bot.store
  (:require
   [clojure.edn :as edn]
   [integrant.core :as ig]
   [duct.logger :as logger]
   [jp.nijohando.failable :as f]
   [jp.nijohando.fs :as fs]))

(defn- load-data
  [path]
  (if (fs/exists? path)
    (f/succ->* (slurp path)
               (edn/read-string))
    {}))

(defn- save-data
  [path data]
  (f/succ->>* (pr-str data)
              (spit path)))

(defprotocol Store
  (-transact! [this f args]))

(defn transact!
  [store f & args]
  (.-transact! store f args))

(defn store
  [{:keys [path logger]}]
  (when-let [p (fs/parent path)]
    (when-not (fs/exists? p)

      ))
  (f/slet [p (fs/path path)
           _ (when-let [dir (fs/parent p)]
               (when-not (or (fs/exists? dir) (fs/dir? dir))
                 (logger/log logger :error :directory-not-found path)
                 (-> (f/fail :directory-not-found)
                     (assoc :path path))))
           path-agent (agent p)
           data (load-data p)
           state (ref data)]
    (reify
      clojure.lang.IDeref
      (deref [this] @state)
      Store
      (-transact! [_ f args]
        (dosync
          (apply alter state f args)
          (send-off path-agent (fn [path]
                                 (f/when-fail* [err (save-data path @state)]
                                   (logger/log logger :error :store-error err))
                                 path)))))))

(defmethod ig/init-key :jp.nijohando.chabonze.bot/store [_ options]
  (-> (store options)
      (f/ensure)))

