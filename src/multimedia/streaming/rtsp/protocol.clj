(ns multimedia.streaming.rtsp.protocol
  "The `multimedia.streaming.rtsp.protocol` namespace defines
  functions for marshalling between RTSP messages and ring-style
  requests and responses."
  (:require [clojure.string :as string]
            [gloss.core :as codec])
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

(defn ->header
  "`->header` converts a key-value pair into a header field."
  [[k v]]
  (let [field (if (re-matches #".*-.*" (name k))
                (->> (string/split (name k) #"-")
                     (map string/capitalize)
                     (string/join \-))
                (name k))
        value (if (vector? v) (string/join \, v) v)]
    (str field ": " value)))

(defn with-content-length
  "Returns `headers` with a `:content-length` entry appended suitable
  for `body`."
  [body headers]
  (if (string/blank? body)
    headers
    (assoc headers :content-length (count (.getBytes body)))))

(defn encode
  "`encode` takes a ring-like `request` map and encodes it into a
  textual RTSP request."
  [{:keys [method url c-seq version headers body]}]
  (let [request-line (str method \space url \space version crlf)
        header-list (str (->> headers
                              (with-content-length body)
                              (#(assoc % :CSeq c-seq))
                              (map ->header)
                              (string/join crlf))
                         crlf
                         crlf)
        body (if (string/blank? body) body (str crlf body))]
    (str request-line
         header-list
         body)))

(defn decode [value]
  (String. value))
