(ns jp.nijohando.chabonze.bot.store-test
  (:require [clojure.test :as t :refer [run-tests is are deftest testing use-fixtures]]
            [clojure.java.io :as jio]
            [jp.nijohando.fs :as fs]
            [jp.nijohando.chabonze.bot.store :as store])
  (:import (java.nio.file Path
                          Files)))

(def dir (atom nil))

(defn- with-test-dir
  [f]
  (fs/with-cwd (fs/create-temp-dir "chabonze.bot.store_")
    (f)
    (fs/delete-dir fs/*cwd*)))

(use-fixtures :each with-test-dir)

(deftest save-data
  (testing "Store data can be created"
    (let [func #'store/save-data
          path (fs/path fs/*cwd* "foo.edn")
          data {:foo "foo1"
                :bar "bar1"}]
      (func path data)
      (is (= data (-> (slurp path)
                      read-string)))))
  (testing "Store data can be updated"
    (let [func #'store/save-data
          path (fs/path fs/*cwd* "foo2.edn")
          data-before {:baz "baz"}
          data-after {:foo "foo1"
                      :bar "bar1"}]
      (spit path (pr-str data-before))
      (is (fs/exists? path))
      (is (= data-before (-> (slurp path)
                             (read-string))))
      (func path data-after)
      (is (= data-after (-> (slurp path)
                            read-string))))))

(deftest load-data
  (testing "Store data can be loaded"
    (let [func #'store/load-data
          path (fs/path fs/*cwd* "foo.edn")
          data {:foo "foo1"
                :bar "bar1"}]
      (spit path (pr-str data ))
      (is (= data (func path)))))
  (testing "Store data can be loaded as an empty map if the file does not exist."
    (let [func #'store/load-data
          path (fs/path fs/*cwd* "nil.edn")]
      (is (= {} (func path))))))

(deftest store
  (testing "Store can be created with a nonexistent path"
    (let [path (fs/path fs/*cwd* "foo.edn")
          s (store/store {:path path
                              :logger nil})]
      (is (not (nil? s)))
      (is (= {} @s))))
  (testing "Store can be created with a data file"
    (let [path (fs/path fs/*cwd* "foo.edn")
          data {:foo "foo"}
          _ (spit path (pr-str data))
          s (store/store {:path path
                              :logger nil})]
      (is (not (nil? s)))
      (is (= data @s)))))

(deftest transact!
  (testing "Store can be created with a nonexistent path"
    (let [path (fs/path fs/*cwd* "foo.edn")
          s (store/store {:path path
                              :logger nil})]
      (is (not (fs/exists? path)))
      (let [agent (store/transact! s assoc :a "a1" :b "b1")]
        (is (= {:a "a1" :b "b1"} @s))
        (await-for 1000 agent)
        (is (= {:a "a1" :b "b1"} (-> (slurp path)
                                     read-string)))))))
