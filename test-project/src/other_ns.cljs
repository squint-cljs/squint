(ns other-ns
  (:require-macros [macros2 :as m :refer [debug]]))

(prn (debug :foo (+ 1 2 3)))
(prn (m/debug :foo (+ 1 2 3)))
