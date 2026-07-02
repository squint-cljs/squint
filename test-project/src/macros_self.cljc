(ns macros-self
  #?(:cljs (:require-macros [macros-self :refer [twice-m]])))

(defmacro twice-m [x]
  `(* 2 ~x))

(def self-val (twice-m 21))
