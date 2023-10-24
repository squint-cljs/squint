(ns main
  (:require-macros [macros :as m :refer [debug]])
  (:require [other-ns]))

(defn foo []
  (m/debug :foo (+ 1 2 3)))

(foo)
(debug :foo (+ 1 2 3 4))
