(ns macros2)

(defmacro debug [_kwd body]
  `(println ::debug ~body))

(defmacro also [expr]
  `(do
     (println "also" ~expr)
     ~expr))
