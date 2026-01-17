(ns macros
  #?(:cljs (:require ["node:fs" :as fs])))

(defn add-100 [x]
  (+ x 100))

(defmacro with-add-100 [x]
  `(add-100 ~x))

(defmacro debug [_kwd body]
  `(println ::debug ~body))

(defmacro read-config []
  #?(:cljs
     (when (.-emit (js/JSON.parse (fs/readFileSync "config.json" "UTF-8")))
       `(prn :emit!))))
