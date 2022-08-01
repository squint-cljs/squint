(ns macros)

(defmacro do-twice [x]
  `(try (do ~x ~x)
        (finally (prn :done!))))
