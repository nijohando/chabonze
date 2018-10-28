(ns jp.nijohando.chabonze.bot.util
  (:require
   [clojure.string :as string]
   [duct.core.env :refer [env]]
   [duct.logger :as dl]
   [jp.nijohando.failable :as f]))

(def ^:private command-pattern #"^\s*<@(.*)>\s+(/\S+)(.*)")
(def ^:private args-pattern #"([^\"]\S*|\".+?\")\s*")
(def ^:private double-quotation-pattern #"[\u201C\u201D]")
(def ^:private single-quotation-pattern #"[\u2018\u2019]")

(defn- surrounded-with?
  [s]
  (let [start (first s)
        end (last s)]
    (when (= start end)
      start)))

(defn- normalize-slack-text
  [s]
  (-> (string/replace s double-quotation-pattern "\"")
      (string/replace single-quotation-pattern "'")))

(defn- parse-args
  [s]
  (->> (re-seq args-pattern s)
       (map last)
       (map #(let [ch (surrounded-with? %)]
               (if (= ch \")
                 (->> (rest %)
                      drop-last
                      (apply str))
                 (string/trim %))))))

(defn- update-map [m f]
  (reduce-kv (fn [m k v]
               (assoc m k (f k v))) {} m))

(defn require-env
  [ckey]
  (or (not-empty (env ckey))
      (-> (f/fail :no-environment-variable)
          (assoc :name ckey))))

(defn parse-slack-command
  [self-id {:keys [type user subtype text] :as msg}]
  (when (and (= "message" type) (not= self-id user) (empty? subtype))
    (let [[_ id cmd args :as a] (re-matches command-pattern text)]
      (when (= self-id id)
        (->> (normalize-slack-text args)
             parse-args
             (map not-empty)
             (filter some?)
             (concat [cmd]))))))

(defn tabular-text
  [header records]
  (let [count* (comp count str)
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

(defn logfn
  [logger]
  (fn
    ([level event] (dl/log logger level event))
    ([level event data] (dl/log logger level event data))))
