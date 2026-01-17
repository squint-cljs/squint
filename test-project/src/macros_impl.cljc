(ns macros-impl)

(defmacro real-debug [kwd body]
  `(println "real-debug:" ~kwd ~body))
