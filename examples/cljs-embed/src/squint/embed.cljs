(ns squint.embed
  (:require ["/squint/core.js" :as squint-core])
  (:require-macros [squint.embed :refer [js!]]))

(set! js/globalThis.squint_core squint-core)

(def foo (js! (fn [{:keys [a b]}]
                [a b])))

(defn init []
  (prn (foo #js {:a 1 :b 2})))
