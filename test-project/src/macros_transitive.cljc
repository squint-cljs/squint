(ns macros-transitive
  (:require [macro-helpers :as helpers]))

(defmacro wrapper [kwd body]
  `(helpers/real-debug ~kwd ~body))

(defmacro with-format [label body]
  `(helpers/format-output ~label ~body))

(defmacro with-deep-format [label body]
  `(helpers/deep-format ~label ~body))
