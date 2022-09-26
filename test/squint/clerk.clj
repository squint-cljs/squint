(ns squint.clerk
  (:require
   [nextjournal.clerk :as clerk]
   [squint.compiler :as sq]))

^::clerk/no-cache
(clerk/clear-cache!)

^{::clerk/visibility {:code :hide :result :hide}}
(comment
  (clerk/serve! nil)
  (clerk/clear-cache!)
  )

;; The `squint-viewer` shows the compiled JS and evaluates the result in the browser.
;; Code is folded for brevity.
^{::clerk/visibility {:code :fold :result :hide}}
(def squint-viewer
  {:transform-fn clerk/mark-presented
   :render-fn
   '(let [result (reagent/atom "foo")
          import (js/eval "(x) => import(x)")]
      (fn [{:keys [repl unrepl js]}]
        (v/html
         [:div
          [:h2 "Compiled JS:"]
          [:details
           [:summary "Production version"]
           [:pre unrepl]]
          [:details
           [:summary "REPL version"]
           [:pre repl]]
          (let [js (clojure.string/replace repl "'squint-cljs/core.js'" "'https://cdn.jsdelivr.net/npm/squint-cljs@0.0.0-alpha.46/core.js'")
                js (str "globalThis.user = globalThis.user || {};\n" js)
                encoded (js/encodeURIComponent js)
                data-uri (str "data:text/javascript;charset=utf-8;eval=" (js/Date.now) "," encoded)
                eval-fn (fn []
                          (-> (import data-uri)
                              (.then
                               (fn [v]
                                 (reset! result js/globalThis._repl)))
                              (.catch (fn [err]
                                        (reset! result (.-message err))
                                        (js/console.log err)))))]
            (eval-fn)
            [:div
             [:div
              [:h2 "Evaluated result:"]
              [:div (v/inspect @result)]]
             [:div.m-1
              [:button.bg-gray-500.hover:bg-blue-700.text-white.font-bold.py-2.px-4.rounded
               {:on-click eval-fn} "Eval"]]])])))})

;; The `compile-string` function compiles a string, in both REPL and non-REPL
;; mode. The REPL version is evaluated by `squint-viewer` and the result is
;; rendered using clerk. Code is folded for brevity.
^{::clerk/visibility {:code :fold :result :hide}}
(defn compile! [s]
  (clerk/with-viewer squint-viewer
    (let [s (if (string? s)
              s
              (pr-str s))]
      {:repl (binding [sq/*repl* true]
               (sq/compile-string s))
       :unrepl (sq/compile-string s)
       :js s})))

(compile! '(+ 1 2 3))

(compile! '[1 2 3])

(compile!
 '(do
    (defn foo [x] x)
    (foo 1337)))

(compile!
  '(do
    ;; due to a bug in CIDER, there's a string in front of the ns form
    ""(ns squint.clerk
      (:require ["https://cdn.skypack.dev/canvas-confetti$default" :as confetti]))

    (confetti)))

(compile!
 '(do
    ""(ns squint.clerk
        (:require ["https://cdn.jsdelivr.net/npm/squint-cljs@0.0.0-alpha.46/index.js" :as compiler]))
    (compiler/compileString "(assoc! {:a 1} :a 2)")))
