(ns macro-utils)

(defn wrap-brackets [label value]
  (str "[" label "] " value))

(defmacro emit-println [& args]
  `(println ~@args))
