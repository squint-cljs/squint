(ns squint.clerk
  (:require
   [nextjournal.clerk :as clerk]
   [squint.compiler :as sq]
   [clojure.string :as str]))

^{::clerk/visibility {:code :hide :result :hide}}
(comment
  (clerk/serve! nil)
  (clerk/clear-cache!)
  )

^{::clerk/visibility {:code :hide :result :hide}}
(clerk/eval-cljs-str "(set! (.-user js/globalThis) #js {})")

^{::clerk/visibility {:code :hide :result :hide}}
(def squint-viewer
  {:transform-fn clerk/mark-presented
   :render-fn
   '(let [result (reagent/atom "foo")
          import (js/eval "(x) => import(x)")]
      (fn [{:keys [repl unrepl js]}]
        (v/html
         [:div
          [:h2 "Compiled JS:"]
          [:pre unrepl]
          [:h2 "REPL version"]
          [:pre repl]
          (let [js (clojure.string/replace repl "'squint-cljs/core.js'" "'https://cdn.jsdelivr.net/npm/squint-cljs@0.0.0-alpha.46/core.js'")
                encoded (js/encodeURIComponent js)
                data-uri (str "data:text/javascript;charset=utf-8;eval=" (gensym) "," encoded)]
            (-> (import data-uri)
                (.then
                 (fn [v]
                   (reset! result js/globalThis._repl)))
                (.catch (fn [err]
                          (reset! result (.-message err)))))
            [:div
             [:h2 "Evaluated result"]
             [:div (v/inspect @result)]])])))})

(defn squint! [s]
  {:repl (binding [sq/*repl* true]
           (sq/compile-string s))
   :unrepl (sq/compile-string s)
   :js s})

^{::clerk/viewer squint-viewer}
(squint! "(+ 1 2 3)")

^{::clerk/viewer squint-viewer}
(squint! "[1 2 3]")

^{::clerk/viewer squint-viewer}
(squint! "(defn foo [x] x) (foo 1337)")
