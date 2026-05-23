(ns macros2)

(defn add-200 [x]
  (+ x 200))

(defmacro debug [_kwd body]
  `(println ::debug ~body))

(defmacro also [expr]
  `(do
     (println "also" ~expr)
     ~expr))

(defmacro with-add-200 [x]
  `(add-200 ~x))
