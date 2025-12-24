(ns macros-alias
  (:require [macros-impl :as impl]))

(defmacro wrapper [kwd body]
  `(impl/real-debug ~kwd ~body))
