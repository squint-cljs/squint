(ns main
  (:require-macros [macros :as m]))

(prn :dude)

(defn foo []
  (m/debug :foo (+ 1 2 3 4)))

(prn (foo))
