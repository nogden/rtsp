(ns multimedia.streaming.rtsp.transport
  (:refer-clojure :exclude [send])
  (:require [aleph.tcp :as tcp]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [multimedia.streaming.rtsp.protocol :as rtsp]))

(defprotocol Connection
  "A `Connection` to an endpoint using a particular
  transport. E.g. tcp or udp."
  (send! [this message] "Sends `message` to the peer.")
  (receive! [this] "Reads a message from the peer and returns it."))

(defn wrap-duplex-stream
  [tx-fn rx-fn wire]
  (let [tx (s/stream 0 (map tx-fn))
        rx (s/stream 0 (map rx-fn))]
    (s/connect tx wire)
    (s/connect wire rx)
    (s/splice tx rx)))

(defmulti connect-to
  "`connect-to` returns a connection object, suitable for use with
  `send`, that will manage a connection with the endpoint `uri`. The
  implementation that is returned is dependant upon the uri scheme."
  :protocol)

(defmethod connect-to "rtsp" [url]
  (let [peer (d/chain
              (tcp/client (select-keys url [:host :port]))
              #(wrap-duplex-stream rtsp/encode rtsp/decode %))]
    (reify
      Connection
      (send! [this message] (s/put! @peer message))
      (receive! [this] (s/take! @peer)))))
