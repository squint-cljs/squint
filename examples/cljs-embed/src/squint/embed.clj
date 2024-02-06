(ns squint.embed
  (:require [clojure.string :as str]
            [squint.compiler]))

(defmacro js! [& body]
  `(~'js* ~(squint.compiler/compile-string (str/join " " body) {:elide-imports true})))
