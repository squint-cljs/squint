(ns node-test
  (:require
   ["node:assert/strict" :as assert]
   ["node:test" :refer [describe]]))

(defn eq [x y]
  (assert/deepEqual (clj->js x) (clj->js y)))

(defn ^:async foo []
  (let [x (-> (js/await (js/import "../corpus/fns.mjs"))
              .-results
              deref)]
    (eq [3 3 3 0] x)))

(describe
 "fns works" foo)
