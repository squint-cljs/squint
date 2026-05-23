(ns macros-impl)

(defn format-debug [x]
  (str "debug: " x))

(defmacro real-debug [x]
  `(println "real-debug:" ~x))

(defmacro format-wrapper [x]
  `(format-debug ~x))
