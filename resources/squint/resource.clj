(ns squint.resource
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defmacro edn-resource [f]
  (list 'quote (edn/read-string (slurp (io/resource f)))))
