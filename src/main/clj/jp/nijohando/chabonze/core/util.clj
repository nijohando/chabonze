(ns jp.nijohando.chabonze.core.util
  (:require
    [environ.core :refer  [env]]
    [jp.nijohando.failable :refer [flet fail]]
    [clojure.string :as string]))

(defn require-env
  [ckey]
  (or (not-empty (env ckey))
      (fail (str (name ckey) " is not set."))))

(defn update-map [m f]
  (reduce-kv (fn [m k v]
               (assoc m k (f k v))) {} m))

(defn tabular-text
  [header records]
  (flet [count* (comp count str)
         max-lengths (reduce #(->> (update-map %2 (fn [_ v] (count* v))) (merge-with max %1)) {} (cons header records))
         hr-size (reduce-kv (fn [sum k len] (+ sum len 3)) 0 (select-keys max-lengths (keys header)))
         hr-line (->> (->> (range hr-size) (map (fn [_] "-"))) (apply str))
         [pheader & precords] (->> records
                                   (cons header)
                                   (map (fn [m] (update-map m #(format (str "%-" (get max-lengths %1) "s") (str %2))))))
         tos (fn [xs]
               (->> xs
                    (map #(->> (for [k (keys header)]
                                 (get % k))
                               (string/join "   ")))
                    (string/join "\n")))]
    (string/join "\n" [(tos [pheader]) hr-line (tos precords)])))
