(ns multimedia.streaming.rtsp.protocol
  "The `multimedia.streaming.rtsp.protocol` namespace defines
  functions for marshalling between RTSP messages and ring-style
  requests and responses."
  (:require [clojure.string :as string]
            [gloss.core :as codec :refer [defcodec]]
            [gloss.io :as io])
  (:import [java.net URI]))

(def default-port 554)

(def default-scheme "rtsp")

(def crlf
  "Newlines in an RTSP request are represented as carrage-return
  followed by line-feed."
  "\r\n")

(defn split-url [url]
  (let [uri (URI. url)
        port (.getPort uri)]
    {:protocol (or (.getScheme uri) default-scheme)
     :host (.getHost uri)
     :port (if (pos? port) port default-port)}))

(defn join-url [{:keys [protocol host port]}]
  (str protocol "://" host \: port))

(defn ensure-absolute [path]
  (if (re-matches #"^/.+" path)
    (string/replace path #"^/+" "/")
    (str "/" path)))

(defn url-for-path [url path]
  (if (= "*" path)
    path
    (str (join-url url) (ensure-absolute path))))

(defn lower-snake-case [string]
  (-> string
      (string/replace #"[^-][A-Z]" #(str (first %) \- (second %)))
      string/lower-case))

(defn upper-snake-case [string]
  (if (re-matches #"(?i)c-seq" string)
    "CSeq"
    (->> (string/split string #"-")
         (map string/capitalize)
         (string/join \-))))

(defn to-header
  "`to-header` converts a key-value pair into a header field."
  [[k v]]
  (let [field (-> k name upper-snake-case)
        value (if (vector? v) (string/join \, v) v)]
    (string/join ": " [field value])))

(defn header-string [header-map]
  (str (->> header-map
            (map to-header)
            (string/join crlf))))

(defn header-map [header-string]
  (->> (string/split header-string (re-pattern crlf))
       (map #(string/split % #": "))
       (map (fn [[k v]] [(keyword (lower-snake-case k)) v]))
       (into {})))

(defn with-content-length [{headers :headers, body :body :as request}]
  (if (string/blank? body)
    request
    (assoc-in request [:headers :content-length] (count (.getBytes body)))))

;; For when Gloss is fixed to handle this case.
;;
;; (def headers
;;   (let [key (codec/string :ascii :delimiters [": "])
;;         value (codec/string :ascii :delimiters [crlf])]
;;     (codec/repeated [key value] :delimiters [(str crlf crlf)])))

(defn make-body-codec [headers]
  (codec/ordered-map :headers headers
                     :body (codec/string
                            :utf-8
                            :length (Integer. (:content-length headers 0)))))

(defn write-only [_]
  (UnsupportedOperationException. "Codec does not support reading"))

(defn merge-headers-and-body [result]
  (dissoc (merge result (:rest result)) :rest))

(def headers
  (codec/compile-frame
   (codec/string :ascii :delimiters [(str crlf crlf)])
   header-string
   header-map))

(def request
  (let [method (codec/string :ascii :delimiters [\space])
        url (codec/string :ascii :delimiters [\space])
        version (codec/string :ascii :delimiters [crlf])
        body (codec/string :utf-8)]
    {:encoder (codec/compile-frame
               (codec/ordered-map :method method
                                  :url url
                                  :version version
                                  :headers headers
                                  :body body)
               with-content-length
               identity)
     :decoder (codec/compile-frame
               (codec/ordered-map :method method
                                  :url url
                                  :version version
                                  :rest (codec/header headers
                                                      make-body-codec
                                                      write-only))
               identity
               merge-headers-and-body)}))

(def response
  (let [version (codec/string :ascii :delimiters [\space])
        status (codec/string-integer :ascii :delimiters [\space])
        reason (codec/string :ascii :delimiters [crlf])
        body (codec/string :utf-8)]
    {:encoder (codec/compile-frame
               (codec/ordered-map :version version
                                  :status status
                                  :reason reason
                                  :headers headers
                                  :body body)
               with-content-length
               identity)
     :decoder (codec/compile-frame
               (codec/ordered-map :version version
                                  :status status
                                  :reason reason
                                  :rest (codec/header headers
                                                      make-body-codec
                                                      write-only))
               identity
               merge-headers-and-body)}))

(def encode-request (map #(io/encode (request :encoder) %)))

(def decode-request (map #(io/decode (request :decoder) %)))

(def encode-response (map #(io/encode (response :encoder) %)))

(def decode-response (map #(io/decode (response :decoder) %)))
