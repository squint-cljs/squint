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
                       {"squint-cljs/src/squint/core.js" "https://cdn.jsdelivr.net/npm/squint-cljs@0.4.81/src/squint/core.js"
                        "squint-cljs/src/squint/string.js" "https://cdn.jsdelivr.net/npm/squint-cljs@0.4.81/src/squint/string.js"}}))]
                   [:title "Squint"]]
                  [:body {:x-data (->js '{:counter 0
                                          :username "User"})}
                   [:div
                    [:input {:type "text"
                             :x-model "username"}]
                    [:div
                     [:span "Hello "]
                     [:span {:x-text "username"}]
                     [:span ", your name reversed is: "]
                     [:span {:x-text (->js '($str/join (reverse username)))}] "!"]]
                   [:div
                    [:button {:x-on:click (->js '(do
                                                   (set! counter (inc counter))))}
                     "Increment"]
                    [:span {:x-text "counter"}]]]
                  [:script {:type "module"}
                   (h/raw
                    "const squint_core = await import('squint-cljs/src/squint/core.js');
                     const squint_string = await import('squint-cljs/src/squint/string.js');
                     const { default: Alpine } = await import('https://unpkg.com/alpinejs@3.13.3/dist/module.esm.js');
                     Alpine.data('squint_core', squint_core);
                     Alpine.magic('squint_core', () => squint_core);
                     Alpine.magic('str', () => squint_string);
                     Alpine.start();")]]))
     :status 200}))

(srv/run-server #'page {:port 8888})
(println "Server started at http://localhost:8888")
@(promise)
