(ns multimedia.streaming.rtsp.client
  "The `multimedia.streaming.rtsp.client` namespace defines the published
  interface for the client."
  (:require [clojure.string :as string]
            [multimedia.streaming.rtsp.protocol :as rtsp]
            [multimedia.streaming.rtsp.transport :refer [request!
                                                         connect-to
                                                         IRequest]]))

;; # What is RTSP?
;;
;; The Real Time Streaming Protocol, or RTSP, is an application-level
;; protocol for control over the delivery of data with real-time
;; properties. RTSP provides an extensible framework to enable
;; controlled, on-demand delivery of real-time data, such as audio and
;; video. Sources of data can include both live data feeds and stored
;; clips. This protocol is intended to control multiple data delivery
;; sessions, provide a means for choosing delivery channels such as UDP,
;; multicast UDP and TCP, and provide a means for choosing delivery
;; mechanisms based upon RTP (RFC 1889).

;; # The RTSP Session
;;
;; A `Session` represents a single communication session between a
;; client and server.  It identifies the peer to connect to and the
;; protocol version to use.  It also holds the protocol state required
;; for RTSP communication to take place.
;;
;; The lifecycle of a session is as follows.
;;
;; 1. A `Session` is created with the `session` construction function.
;;
;; 1. Requests are made within the session via the `request!` function
;;    of the `multimedia.streaming.rtsp.transport.IRequest` protocol.
;;
;; 1. The `Session` is closed via the `.close` method of the
;;    `java.io.Closeable` interface.
;;
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

;; ## Session Construction

(def ^:private default-session
  "`default-session` provides the default values for a `Session`.
  These may be overridden with options to the `session` construction
  function."
  {:c-seq (atom 1)
   :version "RTSP/1.0"})

(defn session
  "`session` starts a new RTSP session with the peer at `url`.  If
  `options` is provided, it must be a map with one or more of the
  following keys.

  `:version` is optional and specifies the version of the RTSP
  protocol to use for the session.  Its value must be a string.  The
  default is `\"RTSP/1.0\"`.

  `:c-seq` is optional and sepcifies the starting CSeq sequence
  number.  Its value must be an intager.  The default value is `1`

  Note that `session` is a pure function that simply returns a data
  structure representation of the session, the actual connection is
  established upon first request.

      (def peer (session \"rtsp://192.168.10.26\"))

  The transport and port are determined by the scheme and port
  segements of the provided URL respectively.  If no port is provided,
  the default RTSP port of `554` is used."
  ([url] (session url {}))
  ([url options]
   (let [url (rtsp/split-url url)
         options (assoc options :url url
                                :connection (connect-to url))
         options (if-let [c-seq (:c-seq options)]
                   (assoc options :c-seq (atom c-seq))
                   options)]
     (map->Session (merge default-session options)))))

;; # Making Requests
;;
;; Once a session has been obtained, RTSP requests can be made by
;; calling the `request!` function, with a ring-like map
;; describing the request.
;;
;;     (request! peer {:method "DESCRIBE" :path "media.mkv"})
;;
;; Alternatively, there are convenience functions defined for each of
;; the RTSP methods.  See [Convenience Functions][].

;; ## The Request Map
;;
;; The RTSP request map may contain the following fields.
;;
;; `:method` is required and specifies the RTSP method (verb) to use
;; in the request.  Its value is a case-sensitive string. For example
;; `"PLAY"`.
;;
;; `:path` is required and specifies the path section of the RTSP URI
;; to which the request is addressed.  Its value must be one of the
;; followi1ng.
;;
;; - The path component of a URI. E.g. "media/stream-1".  The leading
;;   `/` is optional, so `"/media/stream-1"` is semantically equiverlent
;;   in this case.
;; - The value `"*"` if the request is for the peer itself and
;;   addresses no particular presentation or stream.
;;
;; `:headers` is optional and specifies the RTSP headers to be sent
;; with the request.  Its value must be a map of header names to
;; header values.  For example:
;;
;;     {:accept "application/sdp" :cache-control "none"}
;;
;; Header names may be either strings or keywords, but should be
;; lowercase.  Case conversion as per the RTSP standard is performed
;; automatically.  Values are case sensitive strings.
;;
;; `:body` is optional and specifies the body for the request.  Its
;; value must be a string.
;;
;; `:timeout` is optional and specifies the maximum number of
;; milliseconds that that library will wait for a response.  The
;; default value is `30000` (30 seconds).

;; # Response Handling
;;
;; The RTSP library implements all communication asynchronously.
;; Calls to `request!` and to the convenience functions return a
;; [manifold](https://github.com/ztellman/manifold) deferred that will
;; yeild the response once it has been received.  To retrieve the
;; response, simply call `clojure.core/deref` or use the reader
;; literal `@`.  If the response is available it will be returned
;; immediately, otherise the call to `deref` will block until the
;; response has been received, or until the request times out.  This
;; allows very easy switching between synchronous and asynchronous
;; programming.
;;
;;     ;; Asynchronous
;;     (request! peer {:method "DESCRIBE" :path "media.mkv"})
;;
;;     ;; Synchronous
;;     @(request! peer {:method "DESCRIBE" :path "media.mkv"})
;;
;; This also allows all of manifold's asynchronous programming
;; constructs to be used for response handling.
;;
;; ## The Response Map
;;
;; Like requests, responses take the form of a simple map structure
;; with the following keys.
;;
;; `:version` is the version of the RTSP protocol used to encode the
;; message.
;;
;; `:status` is the integer status code of the response.
;;
;; `:reason` is the textual description associated with the status
;; code.
;;
;; `:headers` is a map of header names to header values representing
;; the headers sent by the peer.
;;
;; `:body` is the body of the message.
;;
;; For example:
;;
;;     @(request! peer {:method "OPTIONS" :path "*"})
;;     ;=> {:version "RTSP/1.0",
;;          :status 200,
;;          :reason "OK",
;;          :headers {:c-seq "2",
;;                    :date "Fri, Nov 06 2015 14:52:22 GMT",
;;                    :public "OPTIONS, DESCRIBE, SETUP,
;;                             TEARDOWN, PLAY, PAUSE,
;;                             GET_PARAMETER, SET_PARAMETER"}
;;          :body ""}
;;
;;

;; # Convenience Functions

(defmacro ^:private def-rtsp-method
  "`def-rtsp-method` defines a function that will call `request!`,
  with an RTSP request map whose method is set to `method-name`, and
  return a deferred representing the response.  Note that the defined
  function has an exlamation mark appended to the name to reflect its
  stateful effects."
  [method-name]
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

;; Convenience functions are provided for each of the methods defined
;; in the RTSP specification and may be used instead of the lower
;; level `request!` function.
;;
;;     (def response (describe! peer "path/to/media"))
;;
;; Note that each function has an exlamation mark appeneded to its
;; name.
(def-rtsp-method options)
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

(defn close!
  "`close!` closes the specified session and terminates its connection
  to the peer.  A closed session can be reused by issuing another
  request, all state will be as if `session` has just been called with
  the original parameters."
  [session]
  (.close session))

(defn- print-session
  "`print-session` defines the textual representation of a `Session`."
  [session writer]
  (.write writer (str "#Session" (select-keys session [:url :version]))))

;; Printing support for the `Session` record.
(defmethod print-method Session [session ^java.io.Writer writer]
  (print-session session writer))

;; Serialisation support for the `Session` record.
(defmethod print-dup Session [session ^java.io.Writer writer]
  (print-session session writer))

;; Pretty printing support for the `Session` record.
(.addMethod clojure.pprint/simple-dispatch Session
  #(print-session % *out*))
