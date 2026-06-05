(ns macros-transitive
  (:require [macros-impl :as impl]))

(defmacro wrapper [x]
  `(impl/real-debug ~x))

(defmacro format-it [x]
  `(impl/format-wrapper ~x))
