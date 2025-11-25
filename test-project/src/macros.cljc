(ns macros
  #?(:cljs (:require ["node:fs" :as fs])))

(defmacro debug [_kwd body]
  `(println ::debug ~body))

(defmacro read-config []
  #?(:cljs
     (when (.-emit (js/JSON.parse (fs/readFileSync "config.json" "UTF-8")))
       `(prn :emit!))))
