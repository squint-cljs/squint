(ns other-ns
  (:require [clojure.string :as str])
  (:require-macros [macros2 :as m :refer [debug]]))

(debug :foo (+ 1 2 3 4))
(m/debug :foo (+ 1 2 3))
