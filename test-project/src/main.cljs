(ns main
  (:require-macros [macros :as m :refer [debug]]))

(defn foo []
  (m/debug :foo (+ 1 2 3 4)))

(prn (foo))
(prn (debug :foo (+ 1 2 3 4)))
