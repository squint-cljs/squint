(ns alpinejs
  (:require [babashka.deps :as deps]
            [hiccup2.core :as h]
            [org.httpkit.server :as srv]
            [cheshire.core :as json]))

(deps/add-deps '{:deps {io.github.squint-cljs/squint {:local/root "../.."}}})

(require '[squint.compiler :as squint])

(def state (atom nil))
(defn ->js [form]
  (let [res (squint.compiler/compile-string* (str form) {:core-alias "$squint_core"})]
    (reset! state res)
    (h/raw (:body res))))

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
                       {"squint-cljs/src/squint/core.js" "https://cdn.jsdelivr.net/npm/squint-cljs@0.4.81/src/squint/core.js"}}))]
                   [:title "Squint"]]
                  [:body
                   [:div {:x-data (->js '{:counter (atom 0)
                                          :counter2 0})}
                    [:button {:x-on:click (->js '(do
                                                   (swap! counter inc)
                                                   (set! counter2 (inc counter2))))}
                     "Increment"]
                    [:span {:x-text "[$squint_core.deref(counter), counter2]"}]]]
                  [:script {:type "module"}
                   (h/raw
                    "const squint_core = await import('squint-cljs/src/squint/core.js');
                     const { default: Alpine } = await import('https://unpkg.com/alpinejs@3.13.3/dist/module.esm.js');
                     Alpine.data('squint_core', squint_core);
                     Alpine.magic('squint_core', () => squint_core);
                     Alpine.start();")]]))
     :status 200}))

(srv/run-server #'page {:port 8888})
(println "Server started at http://localhost:8888")
@(promise)
