(ns macros2)

(defmacro debug [_kwd body]
  `(println ::debug ~body))
