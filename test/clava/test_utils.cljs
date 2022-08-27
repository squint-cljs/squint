(ns clava.test-utils
  (:require
   ["clavascript/core.js" :as cl]
   ["lodash$default" :as ld]
   [clava.compiler :as clava]
   [clojure.test :as t]))

(doseq [k (js/Object.keys cl)]
  (aset js/globalThis k (aget cl k)))

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

(defn jss! [expr]
  (if (string? expr)
    (:body (clava/compile-string* expr))
    (clava/transpile-form expr)))

(defn js! [expr]
  (let [js (jss! expr)]
    [(js/eval js) js]))

(defn jsv! [expr]
  (first (js! expr)))


