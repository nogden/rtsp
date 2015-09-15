;; Project configuration for Real Time Streaming Protocol library.
(set-env!
 :resource-paths #{"src"}
 :dependencies '[[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [dire "0.5.3"]
                 [gloss "0.2.5"]
                 [aleph "0.4.0"]
                 ;; Dev dependencies
                 [it.frbracch/boot-marginalia "LATEST" :scope "test"]
                 [adzerk/boot-test "LATEST" :scope "test"]])

(task-options!
 pom
 {:project 'multimedia.streaming/rtsp
  :version "0.1.0-SNAPSHOT"
  :description "A client and server for the Real Time Streaming Protocol (RTSP)
                as described by RFC 2326."
  :url "http://nogden.github.io/rtsp/"
  :scm {:url "https://github.com/nogden/rtsp"}
  :license {"Eclipse Public License"
            "https://www.eclipse.org/legal/epl-v10.html"}}
 repl
 {:init-ns 'multimedia.streaming.rtsp.client}
 push
 {:repo "clojars-classic"
  :ensure-snapshot true})

(deftask build
  "Build the project jar file"
  []
  (comp (pom) (jar)))

(deftask tests
  "Run the automated tests"
  []
  (require 'adzerk.boot-test)
  (let [run-tests (resolve 'adzerk.boot-test/test)]
    (set-env! :source-paths #{"test"})
    (run-tests)))

(deftask doc
  "Generate the project documentation"
  []
  (require 'it.frbracch.boot-marginalia)
  (let [marginalia (resolve 'it.frbracch.boot-marginalia/marginalia)]
    (marginalia :file "rtsp.html")))
