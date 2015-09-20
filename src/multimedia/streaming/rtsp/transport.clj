(ns multimedia.streaming.rtsp.transport
  (:require [multimedia.streaming.rtsp.protocol :as rtsp]
            [aleph.tcp :as tcp]
            [manifold.deferred :as d]
            [manifold.stream :as s]))

(defprotocol IRequest
  "An object that is able to issue requests."
  (request! [this request]
    "Sends `request` to the peer and returns a promise that will yield
     the response."))

(def default-timeout 30000)

(defn wrap-duplex-stream
  [tx-xform rx-xform wire]
  (let [tx (s/stream 0 tx-xform)
        rx (s/stream 0 rx-xform)]
    (s/connect tx wire)
    (s/connect wire rx)
    (s/splice tx rx)))

(defrecord TcpConnection [url wire]
  IRequest
  (request! [this request]
    (d/chain (or @wire (reset! wire (tcp/client url)))
             #(wrap-duplex-stream rtsp/encode-request rtsp/decode-response %)
             #(do (s/put! % request) %)
             #(s/try-take! % (or (:timeout request) default-timeout)))))

(defmulti connect-to
  "`connect-to` returns an object that implements `IRequest`, which
  can be used to issue requests to the peer at `url` using the
  protocol defined by the url."
  :protocol)

(defmethod connect-to "rtsp" [url]
  (->TcpConnection url (atom nil)))
