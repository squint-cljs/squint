(ns compile-time-cljs
  {:squint/compile-time true}
  (:require [clojure.string :as str]))

(defmacro yell [x]
  `(str/upper-case ~x))

(defn runtime-fn [x]
  (inc x))
