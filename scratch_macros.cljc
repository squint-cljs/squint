(ns scratch-macros)

(defmacro do-twice [& body]
  `(do ~@body ~@body))

(defn tagged [tag & _strs]
  `(~'js* "~{}``" ~tag))
