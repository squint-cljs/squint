(ns main
  (:require-macros [macros :as m #_#_:refer [debug]]))

(defn foo []
  (m/debug :foo (+ 1 2 3 4)))

(prn (foo))
#_(debug :foo (+ 1 2 3 4))
