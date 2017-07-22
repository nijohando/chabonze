(ns jp.nijohando.chabonze.twitter.auth
  (:require
    [jp.nijohando.chabonze.core.slack :as slack]
    [jp.nijohando.chabonze.twitter.conf :as conf]
    [jp.nijohando.chabonze.core.store :as store]
    [jp.nijohando.failable :refer [flet f->> f-> fail guard]]
    [clojure.tools.logging :as log]
    [clojure.string :as string]
    [clojure.tools.cli :refer [parse-opts]]
    [clj-http.client :as http]
    [environ.core :refer [env]]
    [buddy.core.mac :as mac]
    [buddy.core.codecs :as codecs]
    [buddy.core.codecs.base64 :as base64]
    [ring.util.codec :as ruc])
  (:import
    (java.time Instant)))

(def ^:private nonce-chars (let [gen (fn [start end]
                             (range (int start) (inc (int end))))]
                   (map char (concat (gen \a \z) (gen \A \Z) (gen \0 \9)))))

(declare sign)

(defn- current-time-second
  []
  (.getEpochSecond (Instant/now)))

(defn- create-signature-base-string
  [req]
  (let [method (:method req)
        url (-> req :url ruc/url-encode)
        params (->> (apply merge ((juxt :query-params :form-params :oauth-params) req))
                    (map (fn [[k v]]
                           [(-> k name ruc/url-encode) (ruc/url-encode v)]))
                    (sort-by first)
                    (map (fn [[k v]]
                           (str k "=" v)))
                    (string/join "&")
                    ruc/url-encode)]
    (str method "&" url "&" params)))

(defn- create-oauth-signature
  [req]
  (flet [sbs (create-signature-base-string req)
         token-secret (store/get-in [:twitter :oauth :token-secret])
         skey (str (-> (conf/oauth-consumer-secret)
                       (ruc/url-encode))
                   "&"
                   (some-> token-secret
                           (ruc/url-encode)))
         h (mac/hash sbs {:key skey :alg :hmac :digest :sha1})]
    (-> (mac/hash sbs {:key skey :alg :hmac :digest :sha1})
        (base64/encode)
        (codecs/bytes->str))))

(defn- append-oauth-signature
  [req]
  (f->> (create-oauth-signature req)
        (assoc-in req [:oauth-params :oauth_signature])))

(defn- generate-oauth-nonce
  []
  (->> (->> (range 30)
            (map (fn [_] (rand-nth nonce-chars))))
       (apply str)))

(defn- create-oauth-params
  []
  (flet [ckey (conf/oauth-consumer-key)
         token (store/get-in [:twitter :oauth :token])]
    (merge
      {:oauth_consumer_key ckey
       :oauth_nonce (generate-oauth-nonce)
       :oauth_signature_method "HMAC-SHA1"
       :oauth_timestamp (current-time-second)
       :oauth_version "1.0"}
      (when token
        {:oauth_token token}))))

(defn- create-authorization-header
  [req]
  (->> (:oauth-params req)
       (sort-by key)
       (map (fn [[k v]] (str (name k) "=\"" (ruc/form-encode v) "\"")))
       (string/join ",")
       (str "OAuth ")))

(defn- request-token
  []
  (flet [_ (store/transact! #(update-in % [:twitter] dissoc :oauth))
         req (sign {:url "https://api.twitter.com/oauth/request_token"
                    :oauth-params {:oauth_callback "oob"}
                    :method "POST"})]
    (http/request req)))

(defn- authorize
  []
  (flet [oauth-token (store/get-in [:twitter :oauth :token-secret])
         req (sign {:url "https://api.twitter.com/oauth/authroize"
                    :method "GET"
                    :query-params {:oauth_token oauth-token}})]
    (http/request req)))

(defn- access-token
  [pin-code]
  (flet [req (sign {:url "https://api.twitter.com/oauth/access_token"
                    :method "POST"
                    :oauth-params {:oauth_verifier pin-code}})]
    (http/request req)))

(defn- account-settings
  []
  (flet [req (sign {:url "https://api.twitter.com/1.1/account/settings.json"
                    :method "GET"})]
    (http/request req)))

(defn- request-authorization
  [slack-msg]
  (flet [_ (slack/typing slack-msg)
         response (request-token)
         result (->> (string/split (:body response) #"&")
                      (map #(let [[k v] (string/split % #"=")]
                              [(keyword k) v]))
                      (into {}))
         {:keys [oauth_token oauth_token_secret]} result
         _ (store/transact! #(-> %
                                 (assoc-in [:twitter :oauth :token] oauth_token)
                                 (assoc-in [:twitter :oauth :token-secret] oauth_token_secret)))]
    (slack/reply-message slack-msg (str "https://api.twitter.com/oauth/authorize?oauth_token=" oauth_token))))

(defn- submit-pincode
  [slack-msg pin-code]
  (flet [_ (slack/typing slack-msg)
         response (access-token pin-code)
         result (->> (string/split (:body response) #"&")
                     (map #(let [[k v] (string/split % #"=")]
                             [(keyword k) v]))
                     (into {}))
         {:keys [oauth_token oauth_token_secret screen_name]} result
         _ (store/transact! #(-> %
                                 (assoc-in [:twitter :oauth :token] oauth_token)
                                 (assoc-in [:twitter :oauth :token-secret] oauth_token_secret)
                                 (assoc-in [:twitter :screen-name] screen_name)))]
    (slack/reply-message slack-msg (str "OK. connected to twitter as " screen_name))))

(def ^:private command-options
  [["-r" "--request" "request for issuing new pincode"
    :id :request]
   ["-p" "--pincode PINCODE" "authorize with pincode"
    :id :pincode
    :validate [#(not (nil? (re-matches #"[0-9]+" %))) "Invalid pincode format"]]
   ["-h" "--help"]])

(defn- usage
  [option-summary]
  (->> ["```"
        "Usage: /twitter auth -r"
        "   or: /twitter auth -p <PINCODE>"
        ""
        "Options:"
        option-summary
        "```"]
       (string/join \newline)))

(defn- error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn- validate-args
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args command-options)
        has-option? #(contains? options %)]
    (cond
      (:help options) {:exit-message (usage summary)}
      (and (has-option? :request) (has-option? :pincode)) {:exit-message (usage summary)}
      errors {:exit-message (str (error-msg errors) "\n\n" (usage summary))}
      (has-option? :request) {:action :request}
      (has-option? :pincode) {:action :pincode :options options}
      :else {:exit-message (usage summary)})))

(defn sign
  [req]
  (flet [req* (f->> (update req :oauth-params #(merge (create-oauth-params) %))
                    (append-oauth-signature))
         ah (create-authorization-header req*)]
    (update req* :headers #(merge % {:Authorization ah}))))

(defn execute
  [slack-msg [_ & args]]
  (flet [{:keys [action options exit-message ok?]} (validate-args args)]
    (if exit-message
      (slack/reply-message slack-msg exit-message)
      (condp = action
        :request (request-authorization slack-msg)
        :pincode (submit-pincode slack-msg (:pincode options))))))
