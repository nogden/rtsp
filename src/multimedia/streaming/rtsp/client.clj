(ns multimedia.streaming.rtsp.client
  "The `multimedia.streaming.rtsp.client` namespace defines the published
  interface for the client API."
  (:require [clojure.string :as string]
            [multimedia.streaming.rtsp.protocol :as rtsp]
            [multimedia.streaming.rtsp.transport :refer [request! connect-to IRequest]]))

(def ^:private default-session
  {:c-seq (atom 1)
   :version "RTSP/1.0"})

(defrecord Session [url version connection c-seq]
  IRequest
  (request! [this {:keys [path method headers body timeout]
                   :or {path "", headers {}, body ""}}]
    (let [rtsp-request {:url (rtsp/url-for-path url path)
                        :method method
                        :version version
                        :headers (assoc headers :c-seq (swap! c-seq inc))
                        :body body
                        :timeout timeout}]
      (request! connection rtsp-request)))
  java.io.Closeable
  (close [this]
    (.close connection)
    (reset! c-seq 0)))

(defn- print-session [session writer]
  (.write writer (str "#Session" (select-keys session [:url :version]))))

(defmethod print-method Session [session ^java.io.Writer writer]
  (print-session session writer))

(defmethod print-dup Session [session ^java.io.Writer writer]
  (print-session session writer))

(.addMethod clojure.pprint/simple-dispatch Session
  #(print-session % *out*))

(defn session
  "`session` creates a new RTSP session object, optionally with the
  specified `options`, that may be used to commnicate with the RTSP
  server at `url`."
  ([url] (session url {}))
  ([url options]
   (let [url (rtsp/split-url url)
         options (assoc options :url url
                                :connection (connect-to url))]
     (map->Session (merge default-session options)))))

(defmacro ^:private def-rtsp-method [method-name]
  (let [verb# (-> method-name
                  (string/replace \- \_)
                  string/upper-case)
        fn-name# (symbol (str method-name \!))
        doc# (str "Sends an RTSP " verb# " request for `path` to the peer described\n"
                  "  by `session` and returns a promise that will yield the response.")
        [s p o] ['session 'path 'options]]
    `(defn ~fn-name# ~doc#
       ([~s ~p] (~fn-name# ~s ~p {}))
       ([~s ~p ~o]
        (request! ~s (assoc ~o :method ~verb#
                               :path ~p))))))

(defn options!
  "Sends an RTSP OPTIONS request to the peer described by `session`
  and returns a promise that will yield the response."
  ([session] (options! session {}))
  ([session options]
   (request! session (assoc options :method "OPTIONS"
                                    :path "*"))))

(def-rtsp-method describe)
(def-rtsp-method announce)
(def-rtsp-method get-parameter)
(def-rtsp-method pause)
(def-rtsp-method play)
(def-rtsp-method record)
(def-rtsp-method redirect)
(def-rtsp-method setup)
(def-rtsp-method set-parameter)
(def-rtsp-method teardown)

(defn close! [session] (.close session))
