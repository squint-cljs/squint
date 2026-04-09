(ns macro-helpers
  (:require [macro-utils :as utils]))

(defmacro real-debug [kwd body]
  `(utils/emit-println "real-debug:" ~kwd ~body))

(defn format-output [label value]
  (str "[" label "] " value))

(defn deep-format [label value]
  (utils/wrap-brackets label value))
