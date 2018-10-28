(ns jp.nijohando.chabonze.bot.slack.web-test
  (:require [clojure.test :as t :refer [run-tests is are deftest testing use-fixtures]]
            [duct.logger :as logger]
            [clj-http.fake :as httpf]
            [ring.middleware.params :refer (wrap-params)]
            [cheshire.core :as json]
            [jp.nijohando.failable :as f]
            [jp.nijohando.deferable :as d]
            [jp.nijohando.chabonze.bot.slack.web :as slack.web]
            [jp.nijohando.chabonze.bot.util :as util]))

(defn mock-logger
  []
  (reify
    logger/Logger
    (-log [_ level ns-str file line id event data]
      (prn level event data))))

(deftest channels
  (let [api-token "123456789"
        web (slack.web/client {:api-token api-token :logger (mock-logger)})]
    (testing "API token must be sent"
      (httpf/with-fake-routes-in-isolation
        {"https://slack.com/api/channels.list" {:post (wrap-params
                                                       (fn [req]
                                                         (is (= api-token (get-in req [:form-params "token"])))
                                                         {:status 200
                                                          :headers {}
                                                          :body (json/generate-string {:ok true :channels [{}]})}))}}
        (is (f/succ? (slack.web/channels web)))))
    (testing "Empty map must be returned when no channels"
      (httpf/with-fake-routes-in-isolation
        {"https://slack.com/api/channels.list" {:post (wrap-params
                                                       (fn [req]
                                                         {:status 200
                                                          :headers {}
                                                          :body (json/generate-string {:ok true :channels []})}))}}
        (is (= {} (slack.web/channels web)))))
    (testing "Failuret must be returned when response status is not 2xx"
      (httpf/with-fake-routes-in-isolation
        {"https://slack.com/api/channels.list" {:post (wrap-params
                                                       (fn [req]
                                                         {:status 500
                                                          :headers {}}))}}
        (let [x (slack.web/channels web)]
          (is (f/fail? x))
          (is (= ::slack.web/failed-to-get-channels @x))
          (let [c1 (f/cause x)
                ed (ex-data (f/cause c1))]
            (is (= ::f/exception @c1))
            (is (= 500 (:status ed)))))))
    (testing "Failuret must be returned when response ok is not false"
      (httpf/with-fake-routes-in-isolation
        {"https://slack.com/api/channels.list" {:post (wrap-params
                                                       (fn [req]
                                                         {:status 200
                                                          :headers {}
                                                          :body (json/generate-string {:ok false :error "invalid_auth"})}))}}
        (let [x (slack.web/channels web)]
          (is (f/fail? x))
          (is (= ::slack.web/failed-to-get-channels @x))
          (is (= "invalid_auth" (:error x))))))))


(deftest channel
  (let [api-token "123456789"
        web (slack.web/client {:api-token api-token :logger (mock-logger)})]
    (testing "API token must be sent"
      (httpf/with-fake-routes-in-isolation
        {"https://slack.com/api/channels.info" {:post (wrap-params
                                                       (fn [req]
                                                         (is (= api-token (get-in req [:form-params "token"])))
                                                         {:status 200
                                                          :headers {}
                                                          :body (json/generate-string {:ok true :channel {}})}))}}
        (is (f/succ? (slack.web/channel web "AAAA")))))
    (testing "Failuret must be returned when response status is not 2xx"
      (httpf/with-fake-routes-in-isolation
        {"https://slack.com/api/channels.info" {:post (wrap-params
                                                       (fn [req]
                                                         {:status 500
                                                          :headers {}}))}}
        (let [x (slack.web/channel web "AAAA")]
          (is (f/fail? x))
          (is (= ::slack.web/failed-to-get-channel @x))
          (let [c1 (f/cause x)
                ed (ex-data (f/cause c1))]
            (is (= ::f/exception @c1))
            (is (= 500 (:status ed)))))))
    (testing "Failuret must be returned when response ok is not false"
      (httpf/with-fake-routes-in-isolation
        {"https://slack.com/api/channels.info" {:post (wrap-params
                                                      (fn [req]
                                                        {:status 200
                                                         :headers {}
                                                         :body (json/generate-string {:ok false :error "channel_not_found"})}))}}
        (let [x (slack.web/channel web "AAAA")]
          (is (f/fail? x))
          (is (= ::slack.web/failed-to-get-channel @x))
          (is (= "channel_not_found" (:error x))))))))

(deftest find-channel
  (let [api-token "123456789"
        web (slack.web/client {:api-token api-token :logger (mock-logger)})]
    (testing "API token must be sent"
      (httpf/with-fake-routes-in-isolation
        {"https://slack.com/api/channels.list" (wrap-params
                                                (fn [req]
                                                  (is (= api-token (get-in req [:form-params "token"])))
                                                  {:status 200
                                                   :headers {}
                                                   :body (json/generate-string {:ok true :channels [{:id "A001" :name "foo"}]})}))}
        (is (f/succ? (slack.web/find-channel web "foo")))))
    (testing "Failuret must be returned when response status is not 2xx"
      (httpf/with-fake-routes-in-isolation
        {"https://slack.com/api/channels.list" {:post (wrap-params
                                                       (fn [req]
                                                         {:status 500
                                                          :headers {}}))}}
        (let [x (slack.web/find-channel web "foo")]
          (is (f/fail? x))
          (is (= ::slack.web/failed-to-find-channel @x))
          (let [c1 (f/cause x)]
            (is (= ::slack.web/failed-to-get-channels @c1))))))
    (testing "Failuret must be returned when response ok is not false"
      (httpf/with-fake-routes-in-isolation
        {"https://slack.com/api/channels.list" {:post (wrap-params
                                                       (fn [req]
                                                         {:status 200
                                                          :headers {}
                                                          :body (json/generate-string {:ok false :error "invalid_auth"})}))}}
        (let [x (slack.web/find-channel web "foo")]
          (is (f/fail? x))
          (is (= ::slack.web/failed-to-find-channel @x))
          (let [c1 (f/cause x)]
            (is (= ::slack.web/failed-to-get-channels @c1))))))
    (testing "Failuret must be returned when channel is not found"
      (httpf/with-fake-routes-in-isolation
        {"https://slack.com/api/channels.list" {:post (wrap-params
                                                       (fn [req]
                                                         {:status 200
                                                          :headers {}
                                                          :body (json/generate-string {:ok true :channels [{:id "A001" :name "foo"}]})}))}}
        (let [x (slack.web/find-channel web "bar")]
          (is (f/fail? x))
          (is (= ::slack.web/channel-not-found @x))
          (is (= "bar" (:name x))))))))

(deftest post-message
  (let [api-token "123456789"
        web (slack.web/client {:api-token api-token :logger (mock-logger)})]
    (testing "API token must be sent"
      (httpf/with-fake-routes-in-isolation
        {"https://slack.com/api/chat.postMessage" {:post (wrap-params
                                                          (fn [req]
                                                            (is (= api-token (get-in req [:form-params "token"])))
                                                            {:status 200
                                                             :headers {}
                                                             :body (json/generate-string {:ok true})}))}}
        (is (f/succ? (slack.web/post-message web {:channel "C001" :text "foo"})))))
    (testing "Parameters must be sent"
      (httpf/with-fake-routes-in-isolation
        {"https://slack.com/api/chat.postMessage" {:post (wrap-params
                                                          (fn [req]
                                                            (is (= "C001" (get-in req [:form-params "channel"])))
                                                            (is (= "foo" (get-in req [:form-params "text"])))
                                                            {:status 200
                                                             :headers {}
                                                             :body (json/generate-string {:ok true})}))}}
        (is (f/succ? (slack.web/post-message web {:channel "C001" :text "foo"})))))
    (testing "Failuret must be returned when response status is not 2xx"
      (httpf/with-fake-routes-in-isolation
        {"https://slack.com/api/chat.postMessage" {:post (wrap-params
                                                          (fn [req]
                                                            (is (= "C001" (get-in req [:form-params "channel"])))
                                                            (is (= "foo" (get-in req [:form-params "text"])))
                                                            {:status 500
                                                             :headers {}}))}}
        (let [x (slack.web/post-message web {:channel "C001" :text "foo"})]
          (is (f/fail? x))
          (is (= ::slack.web/failed-to-post-message @x))
          (let [c1 (f/cause x)
                ed (ex-data (f/cause c1))]
            (is (= ::f/exception @c1))
            (is (= 500 (:status ed)))))))
    (testing "Failuret must be returned when response ok is not false"
      (httpf/with-fake-routes-in-isolation
        {"https://slack.com/api/chat.postMessage" {:post (wrap-params
                                                          (fn [req]
                                                            (is (= "C001" (get-in req [:form-params "channel"])))
                                                            (is (= "foo" (get-in req [:form-params "text"])))
                                                            {:status 200
                                                             :headers {}
                                                             :body (json/generate-string {:ok false})}))}}
        (let [x (slack.web/post-message web {:channel "C001" :text "foo"})]
          (is (f/fail? x))
          (is (= ::slack.web/failed-to-post-message @x)))))))

