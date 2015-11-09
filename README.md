# rtsp
A [Clojure](http://clojure.org) based, asynchronous client and
server for the Real Time Streaming Protocol (RTSP) as described by
[RFC 2326](https://tools.ietf.org/html/rfc2326).

## Installation

Available on Clojars:

Link goes here.

## Documentation

Full API and implementation documentation is available
[here](http://nogden.github.io/rtsp/).

## Usage

```clojure
(ns example
  (require [multimedia.streaming.rtsp.client :refer :all]))

;; Create an RTSP session
(def peer (session "rtsp://192.168.1.73"))
peer ;=> #Session{:url {:protocol "rtsp",
                        :port 8554,
                        :host "192.168.1.73"},
                  :version "RTSP/1.0"}

;; Issue requests as ring-like request maps
(def response (request! peer {:method "DESCRIBE"
                              :path "media/stream-1"}))

;; Or use the provided convenience functions
(def response (describe! peer "media/stream-1"))

;; All communication is asynchronous and non-blocking.
;; Requests return [manifold](https://github.com/ztellman/manifold) deferreds.
response ;=> #<Deferred@19c15de7: :not-delivered>

@response ;=> {:version "RTSP/1.0",
               :status 200,
               :reason "OK",
               :headers {:c-seq "4",
                         :content-base "rtsp://192.168.1.73:554/media/stream-1/",
                         :content-length "979",
                         :content-type "application/sdp",
                         :date "Fri, Nov 06 2015 16:00:16 GMT"},
               :body "v=0\r\no=- 1446825616551909 1 IN IP4 192.168.1.73\r\n..."}

;; Optional manual closing of connections
(close! peer)
```

## Still to Come

1. Server functionality
2. UDP (rtspu) transport support

## License

Copyright © 2015 Nick Ogden

Distributed under the Eclipse Public License, the same as Clojure.
