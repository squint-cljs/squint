(ns macros)

(defmacro debug [_kwd body]
  `(println ::debug ~body))
