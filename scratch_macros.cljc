(ns scratch-macros)

(defmacro do-twice [& body]
  `(do ~@body ~@body))

