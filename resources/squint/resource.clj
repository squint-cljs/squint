(ns squint.resource
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defmacro edn-resource [f]
  (list 'quote (edn/read-string (slurp (io/resource f)))))

(defmacro version []
  (->> (slurp "package.json")
       (str/split-lines)
       (some #(when (str/includes? % "version") %))
       (re-find #"\d+\.\d+\.\d+")))
