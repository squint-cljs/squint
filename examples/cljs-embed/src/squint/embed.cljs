(ns squint.embed
  (:require ["/squint/core.js" :as squint-core])
  (:require-macros [squint.embed :refer [js!]]))

(set! js/globalThis.squint_core squint-core)

(def bar 1)

(def foo (js! (fn [{:keys [a b]}]
                (+ a b
                   ;; caveat, you need to fully qualify references to other namespaces
                   squint.embed/bar))))

(defn init []
  (js/console.log (foo #js {:a 1 :b 2})))
