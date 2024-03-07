(ns index
  (:require [babashka.deps :as deps]
            [hiccup2.core :as h]
            [org.httpkit.server :as srv]
            [cheshire.core :as json]))

(deps/add-deps '{:deps {io.github.squint-cljs/squint {:local/root "../.."}}})

(require '[squint.compiler :as squint])

(def state (atom nil))
(defn ->js [form]
  (let [res (squint.compiler/compile-string* (str form))]
    (reset! state res)
    (:body res)))

(defn page [_]
  {:body (str (h/html
               [:html
                [:head
                 [:link {:rel "stylesheet"
                         :href "https://cdn.jsdelivr.net/npm/water.css@2/out/light.css"}]
                 [:script {:type "importmap"}
                  (h/raw
                   (json/generate-string
                    {:imports
                     {"squint-cljs/src/squint/core.js" "https://cdn.jsdelivr.net/npm/squint-cljs@0.4.81/src/squint/core.js"}}))]
                 [:script {:type "module"}
                  (h/raw
                   "globalThis.squint_core = await import('squint-cljs/src/squint/core.js');")]
                 [:title "Squint"]]
                [:body
                 [:div "Click the button to update counter!"]
                 [:button
                  {:onclick (->js '(let [elt (js/document.getElementById "counter")
                                         val (-> (.-innerText elt) parse-long)]
                                     (set! elt -innerText (inc val))))}
                  "Click me"]
                 [:div "The counter value:"
                  [:span {:id "counter"} "0"]]]]))
   :status 200})

(srv/run-server #'page {:port 8888})
(println "Server started at http://localhost:8888")
@(promise)
