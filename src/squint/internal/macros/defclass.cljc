(ns squint.internal.macros.defclass)

(defn defclass [_ _ & body]
  `(defclass* ~@body))
