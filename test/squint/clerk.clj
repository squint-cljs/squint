(ns squint.clerk
  (:require
   [nextjournal.clerk :as clerk]
   [squint.compiler :as sq]
   [clojure.string :as str]))

^::clerk/no-cache
(clerk/clear-cache!)

^{::clerk/visibility {:code :hide :result :hide}}
(comment
  (clerk/serve! nil)
  (clerk/clear-cache!)
  )

;; The `squint-viewer` shows the compiled JS and evaluates the result in the browser.
;; Code is folded for brevity.
;; TODO: multiviewer
;; See Clerk book https://github.clerk.garden/nextjournal/book-of-clerk/commit/d74362039690a4505f15a61112cab7da0615e2b8/
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
                js (clojure.string/replace js "'squint-cljs/string.js'" "'https://cdn.jsdelivr.net/npm/squint-cljs@0.0.0-alpha.46/string.js'")
                js (str "globalThis.user = globalThis.user || {};\n" js)
                encoded (js/encodeURIComponent js)
                data-uri (str "data:text/javascript;charset=utf-8;eval="  "," encoded)
                eval-fn (fn []
                          (-> (import data-uri)
                              (.then (fn [v]
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
  (let [s (if (string? s)
            s
            (pr-str s))]
    {:type ::squint-result
     :repl (binding [sq/*repl* true]
             (sq/compile-string s))
     :unrepl (sq/compile-string s)
     :js s}))

(clerk/add-viewers!
 [{:pred #(= (:type %) ::squint-result) :transform-fn (clerk/update-val #(clerk/with-viewer squint-viewer %))}])

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
        (:require ["https://cdn.jsdelivr.net/npm/squint-cljs@0.0.0-alpha.46/index.js" :as compiler]
                  [clojure.string :as string]))
    (def js-str (str (compiler/compileString "(assoc! {:a 33} :a 2)")))
    (let [_ (prn :js-str js-str)
          js-str (.replaceAll js-str "squint-cljs/core.js" "https://cdn.jsdelivr.net/npm/squint-cljs@0.0.0-alpha.46/core.js")
          _ (prn :foo1 js-str)
          js-str (.replaceAll js-str "'squint-cljs/string.js'" "'https://cdn.jsdelivr.net/npm/squint-cljs@0.0.0-alpha.46/string.js'")
          _ (prn :foo2 js-str)
          encoded (js/encodeURIComponent js-str)
          data-uri (+ "data:text/javascript;charset=utf-8;eval=" #_(js/Date.now) "," encoded)]
      (-> (js/import data-uri)
          (.then #(prn :dude js/globalThis._repl))
          (.then (constantly js/globalThis._repl))))))
