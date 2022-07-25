(ns node-test
  (:require
   ["node:assert/strict" :as assert]
   ["node:test" :refer [describe]]))

(defn eq [x y]
  (assert/deepEqual (clj->js x) (clj->js y)))

(defn ^:async foo []
  (try (let [^:js {:keys [results]} (js/await (js/import "../corpus/fns.mjs"))]
         (eq [3 3 3 0] @results))
       (catch :default e
         (assert/fail (ex-message e)))))

(describe
 "fns works" (assert/doesNotReject foo))
