(ns jp.nijohando.chabonze.twitter.auth
  (:require
   [clojure.string :as string]
   [clojure.tools.cli :as cli]
   [clj-http.client :as http]
   [integrant.core :as ig]
   [duct.logger :as dl]
   [buddy.core.mac :as mac]
   [buddy.core.codecs :as codecs]
   [buddy.core.codecs.base64 :as base64]
   [ring.util.codec :as ruc]
   [jp.nijohando.chabonze.bot.slack.rtm :as slack.rtm]
   [jp.nijohando.chabonze.bot.store :as store]
   [jp.nijohando.chabonze.bot.util :as util]
   [jp.nijohando.chabonze.bot.command :as cmd]
   [jp.nijohando.failable :as f])
  (:import
   (java.time Instant)))

(def ^:private nonce-chars (let [gen (fn [start end]
                                       (range (int start) (inc (int end))))]
                             (map char (concat (gen \a \z) (gen \A \Z) (gen \0 \9)))))

(defprotocol Service
  (-sign [this req]))

(defn sign
  [srv req]
  (-sign srv req))

(defn- generate-oauth-nonce
  []
  (->> (->> (range 30)
            (map (fn [_] (rand-nth nonce-chars))))
       (apply str)))

(defn- parse-response-body
  [response]
  (->> (string/split (:body response) #"&")
       (map #(let [[k v] (string/split % #"=")]
               [(keyword k) v]))
       (into {})))

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

(defn- create-authorization-header [req]
  (->> (:oauth-params req)
       (sort-by key)
       (map (fn [[k v]] (str (name k) "=\"" (ruc/form-encode v) "\"")))
       (string/join ",")
       (str "OAuth ")))

(defn- validate-args
  [args]
  (let [cli-opts [["-r" "--request" "request for issuing new pincode"
                   :id :request]
                  ["-p" "--pincode PINCODE" "authorize with pincode"
                   :id :pincode
                   :validate [#(not (nil? (re-matches #"[0-9]+" %))) "Invalid pincode format"]]
                  ["-h" "--help"]]
        {:keys [options arguments errors summary]} (cli/parse-opts args cli-opts)
        has-option? #(contains? options %)
        usage (fn [option-summary]
                (->> ["```"
                      "Usage: /twitter auth -r"
                      "   or: /twitter auth -p <PINCODE>"
                      ""
                      "Options:"
                      option-summary
                      "```"]
                     (string/join \newline)))
        error-msg (fn [errors]
                    (str "The following errors occurred while parsing your command:\n\n"
                         (string/join \newline errors)))]
    (cond
      (:help options) {:exit-message (usage summary)}
      (and (has-option? :request) (has-option? :pincode)) {:exit-message (usage summary)}
      errors {:exit-message (str (error-msg errors) "\n\n" (usage summary))}
      (has-option? :request) {:action :request}
      (has-option? :pincode) {:action :pincode :options options}
      :else {:exit-message (usage summary)})))

(defn service
  [{:keys [rtm
           store
           logger
           oauth-consumer-key
           oauth-consumer-secret
           connect-timeout
           socket-timeout]
    :or {connect-timeout 1000
         socket-timeout 1000}}]
  (letfn [(request-token []
            (f/succ->*
             {:url "https://api.twitter.com/oauth/request_token"
              :oauth-params {:oauth_callback "oob"}
              :socket-timeout socket-timeout
              :conn-timeout connect-timeout
              :method "POST"}
             sign
             http/request))
          (access-token [pincode]
            (f/succ->*
             {:url "https://api.twitter.com/oauth/access_token"
              :method "POST"
              :socket-timeout socket-timeout
              :conn-timeout connect-timeout
              :oauth-params {:oauth_verifier pincode}}
             sign
             http/request))
          (save [orders]
            (store/transact!
             store
             (fn [state]
               (loop [s state [[key value :as order] & more] (into [] orders )]
                 (if (nil? order)
                   s
                   (recur (assoc-in s key value) more))))))
          (create-oauth-signature [req]
            (let [sbs (create-signature-base-string req)
                  token-secret (get-in @store [:twitter :oauth :token-secret])
                  skey (str (-> oauth-consumer-secret
                                (ruc/url-encode))
                            "&"
                            (some-> token-secret
                                    (ruc/url-encode)))
                  h (mac/hash sbs {:key skey :alg :hmac :digest :sha1})]
              (-> (mac/hash sbs {:key skey :alg :hmac :digest :sha1})
                  (base64/encode)
                  (codecs/bytes->str))))
          (create-oauth-params []
            (let [token (get-in @store [:twitter :oauth :token])]
              (merge
               {:oauth_consumer_key oauth-consumer-key
                :oauth_nonce (generate-oauth-nonce)
                :oauth_signature_method "HMAC-SHA1"
                :oauth_timestamp (.getEpochSecond (Instant/now))
                :oauth_version "1.0"}
               (when token
                 {:oauth_token token}))))
          (append-oauth-signature [req]
            (->> (create-oauth-signature req)
                 (assoc-in req [:oauth-params :oauth_signature])))
          (sign [req]
            (let [r (->> (update req :oauth-params #(merge (create-oauth-params) %))
                         append-oauth-signature)
                  ah (create-authorization-header r)]
              (update r :headers #(merge % {:Authorization ah}))))
          (request-token-command [{:keys [channel] :as msg}]
            (slack.rtm/send-typing rtm channel)
            (store/transact! store #(update % :twitter dissoc :oauth))
            (f/if-succ [{:keys [oauth_token oauth_token_secret] :as x} (f/succ->> (request-token)
                                                                                  parse-response-body)]
              (do
                (save {[:twitter :oauth :token] oauth_token
                       [:twitter :oauth :token-secret] oauth_token_secret})
                (slack.rtm/send-message rtm channel (str "https://api.twitter.com/oauth/authorize?oauth_token=" oauth_token)))
              (do
                (dl/log logger :error :failed-to-get-request-token x)
                (slack.rtm/send-message rtm channel (str "Opps! Failed to get request token.")))))
          (submit-pincode-command [{:keys [channel] :as msg} {:keys [pincode] :as cli-opts}]
            (slack.rtm/send-typing rtm channel)
            (f/if-succ [x (access-token pincode)]
              (let [result (->> (string/split (:body x) #"&")
                                (map #(let [[k v] (string/split % #"=")]
                                        [(keyword k) v]))
                                (into {}))
                    screen_name (:screen_name result)]
                (save {[:twitter :oauth :token] (:oauth_token result)
                       [:twitter :oauth :token-secret] (:oauth_token_secret result)
                       [:twitter :screen-name] screen_name})
                (slack.rtm/send-message rtm channel (str "OK. connected to twitter as " screen_name)))
              (do
                (dl/log logger :error :failed-to-get-access-token x)
                (slack.rtm/send-message rtm channel (str "Opps! Failed to get access token.")))))]
    (reify
      cmd/Command
      (name [this]
        "auth")
      (description [this]
        "Authorize the bot to access Twitter.")
      (execute [this {:keys [channel] :as msg} args]
        (let [{:keys [action exit-message ok?] cli-opts :options} (validate-args args)]
          (if exit-message
            (slack.rtm/send-message rtm channel exit-message)
            (condp = action
              :request (request-token-command msg)
              :pincode (submit-pincode-command msg cli-opts)))))
      Service
      (-sign [this req]
        (sign req)))))

(defmethod ig/init-key :jp.nijohando.chabonze.twitter/auth [_ options]
  (service options))
