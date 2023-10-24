(ns squint.test-utils
  (:require
   ["lodash$default" :as ld]
   ["squint-cljs/core.js" :as cl]
   ["squint-cljs/string.js" :as clstr]
   [clojure.test :as t]
   [squint.compiler :as squint]))

(doseq [k (js/Object.keys cl)]
  (aset js/globalThis k (aget cl k)))

(let [mut #js {}]
  (aset js/globalThis "squint.string" mut)
  (doseq [k (js/Object.keys clstr)]
    (aset mut k (aget clstr k))))

(defn eq [a b]
  (ld/isEqual (clj->js a) (clj->js b)))

(def old-fail (get-method t/report [:cljs.test/default :fail]))

(defmethod t/report [:cljs.test/default :fail] [m]
  (set! js/process.exitCode 1)
  (old-fail m))

(def old-error (get-method t/report [:cljs.test/default :fail]))

(defmethod t/report [:cljs.test/default :error] [m]
  (set! js/process.exitCode 1)
  (old-error m))

(defn jss!
  ([expr] (jss! expr nil))
  ([expr opts]
   (if (string? expr)
     (:body (squint/compile-string* expr (merge {:elide-imports true
                                                 :core-alias "squint_core"}
                                                opts)))
     (squint/transpile-form expr (merge {:elide-imports true
                                         :core-alias "squint_core"}
                                        opts)))))

(defn js!
  ([expr] (js! expr nil))
  ([expr opts]
   (let [js (jss! expr opts)]
     [(js/eval js) js])))

(defn jsv!
  ([expr] (jsv! expr nil))
  ([expr opts]
   (first (js! expr opts))))


