(ns macros)

(defn add-100 [x] (+ x 100))

(defmacro with-add-100 [x]
  `(add-100 ~x))
