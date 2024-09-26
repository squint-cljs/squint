(ns preact
  (:require [babashka.deps :as deps]
            [babashka.fs :as fs]
            [hiccup2.core :as h]
            [org.httpkit.server :as srv]
            [cheshire.core :as json]))

(deps/add-deps '{:deps {io.github.squint-cljs/squint {:local/root "../.."}}})

(require '[squint.compiler :as squint])

(def cljs (squint/compile-string (slurp (fs/file (fs/parent *file*) "preact.cljs" ))))

(defn page [req]
  (when (= "/" (:uri req))
    {:body (str (h/html
                 [:html
                  [:head
                   [:link {:rel "stylesheet"
                           :href "https://cdn.jsdelivr.net/npm/water.css@2/out/light.css"}]
                   [:script {:type "importmap"}
                    (h/raw
                     (json/generate-string
                      {:imports
                       {"squint-cljs/core.js" "https://cdn.jsdelivr.net/npm/squint-cljs@0.8.113/src/squint/core.js"
                        "squint-cljs/src/squint/core.js" "https://cdn.jsdelivr.net/npm/squint-cljs@0.8.113/src/squint/core.js"
                        "squint-cljs/src/squint/string.js" "https://cdn.jsdelivr.net/npm/squint-cljs@0.8.113/src/squint/string.js"}}))]
                   [:title "Squint"]]
                  [:body
                   [:div {:id "cljs"}]]
                  [:script {:type "module"}
                   cljs]]))
     :status 200}))

(srv/run-server #'page {:port 8888})
(println "Server started at http://localhost:8888")
@(promise)
