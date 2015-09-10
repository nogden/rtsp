(ns multimedia.streaming.rtsp.client
  "The `multimedia.streaming.rtsp.client` namespace defines the published
  interface for the client API."
  (:require [clojure.string :as string]
            [multimedia.streaming.rtsp.protocol :as rtsp]
            [multimedia.streaming.rtsp.transport :as transport])
  (:import [java.net URI]))

(defprotocol IRequest
  (request! [this options]))

(def default-session
  {:state (atom {:c-seq 1})
   :version "RTSP/1.0"})

(defn split-url [url]
  (let [uri (URI. url)
        port (.getPort uri)]
    {:protocol (or (.getScheme uri) "rtsp")
     :host (.getHost uri)
     :port (if (pos? port) port rtsp/default-port)}))

(defn join-url [{:keys [protocol host port]}]
  (str protocol "://" host \: port))

(defn ensure-absolute [path]
  (if (re-matches #"^/.+" path)
    (string/replace path #"^/+" "/")
    (str "/" path)))

(defn request-target-url [url path]
  (if (= "*" path)
    path
    (str (join-url url) (ensure-absolute path))))

(defrecord Session [url version state]
  IRequest
  (request! [this {:keys [path method headers body]}]
    (let [{c-seq :c-seq
           connection :connection} @state
          rtsp-request {:url (request-target-url url path)
                        :method method
                        :version version
                        :c-seq c-seq
                        :headers (or headers {})
                        :body (or body "")}
          connection (or connection (transport/connect-to url))]
      (transport/send! connection rtsp-request)
      (swap! state assoc :connection connection
             :c-seq (inc c-seq))
      (transport/receive! connection))))

(defn print-session [session writer]
  (.write writer (str "#Session" (select-keys session [:url :version]))))

(defmethod print-method Session [session ^java.io.Writer writer]
  (print-session session writer))

(defmethod print-dup Session [session ^java.io.Writer writer]
  (print-session session writer))

(.addMethod clojure.pprint/simple-dispatch Session
  #(print-session % *out*))

(defn session
  "The `session` function creates a new RTSP session structure with
  the specified `options` that may be used to commnicate with the RTSP
  server at `url`."
  ([url] (session url {}))
  ([url options]
   (let [options (assoc options :url (split-url url))]
     (map->Session (merge default-session options)))))

(defn describe!
  "`describe!` sends an RTSP DESCRIBE request for `path` to the peer
  described by `session`."
  ([session path] (describe! session path {}))
  ([session path options]
   (request! session (assoc options :method "DESCRIBE"
                                    :path path))))
(defn options!
  ([session] (options! session {}))
  ([session options]
   (request! session (assoc options :method "OPTIONS"
                                    :path "*"))))

;; (def-rtsp-method announce)
;; (def-rtsp-method get-parameter)
;; (def-rtsp-method options)
;; (def-rtsp-method pause)
;; (def-rtsp-method play)
;; (def-rtsp-method record)
;; (def-rtsp-method redirect)
;; (def-rtsp-method setup)
;; (def-rtsp-method set-parameter)
;; (def-rtsp-method teardown)

