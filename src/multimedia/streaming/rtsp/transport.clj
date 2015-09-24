(ns multimedia.streaming.rtsp.transport
  (:require [multimedia.streaming.rtsp.protocol :as rtsp]
            [aleph.tcp :as tcp]
            [manifold.deferred :as d]
            [manifold.stream :as s]))

;; Interface to be used by all protocols.

(defprotocol IRequest
  "An object that is able to issue requests."
  (request! [this request]
    "Sends `request` to the peer and returns a promise that will yield
     the response."))

(def default-timeout 30000)

(defmulti connect-to
  "`connect-to` returns an object that implements `IRequest`, which
  can be used to issue requests to the peer at `url` using the
  protocol defined by the url."
  :protocol)

;; TCP support

(defn wrap-duplex-stream
  [encoder decoder wire]
  (let [tx (s/stream)]
    (s/connect (s/map encoder tx) wire)
    (s/splice tx (decoder wire))))

(defn existing-connection [connection]
  (when (and @connection (not (s/closed? @connection)))
    @connection))

(defn create-connection! [connection url]
  (-> (d/chain (tcp/client url)
               #(wrap-duplex-stream rtsp/encode-request rtsp/decode-response %)
               #(reset! connection %))
      (d/catch io.netty.channel.ConnectTimeoutException
               #(d/error-deferred
                 (java.net.SocketTimeoutException. (.getMessage %))))))

(defrecord TcpConnection [url wire]
  IRequest
  (request! [this request]
    (d/chain (or (existing-connection wire)
                 (create-connection! wire url))
             #(do (s/put! % request) %)
             #(s/try-take! % (or (:timeout request) default-timeout))))
  java.io.Closeable
  (close [this] (when (existing-connection wire)
                  (swap! wire s/close!))))

(defmethod connect-to "rtsp" [url]
  (->TcpConnection url (atom nil)))
