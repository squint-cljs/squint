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
(def squint-viewer
  {:transform-fn clerk/mark-presented
   :render-fn
   '(let [result (reagent/atom "foo")
          import (js/eval "(x) => import(x)")]
      (fn [x]
        (v/html
         [:div [:pre (clojure.string/replace (str x) "globalThis._repl =" "")]
          "=>"
          (let [x (clojure.string/replace x "'squint-cljs/core.js'" "'https://cdn.jsdelivr.net/npm/squint-cljs@0.0.0-alpha.46/core.js'")
                encoded (js/encodeURIComponent x)
                data-uri (str "data:text/javascript;charset=utf-8;eval=" (gensym) "," encoded)]
            (-> (import data-uri)
                (.then
                 (fn [v]
                   (reset! result js/globalThis._repl)))
                (.catch (fn [_]
                          (reset! result "error"))))
            [:pre (str @result)])])))})

(defn squint! [s]
  (binding [sq/*repl* true]
    (sq/compile-string s)))

^{::clerk/viewer squint-viewer}
(squint! "(+ 1 2 3)")

^{::clerk/viewer squint-viewer}
(squint! "(pr-str [1 2 3])")

^{::clerk/viewer squint-viewer}
(squint! "(defn foo [x] x) (foo 1337)")
