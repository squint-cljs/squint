(ns shared)

(defn shared-function []
  (+ 1 2 3))

(defmulti shape-label :type)
