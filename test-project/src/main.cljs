(ns main
  (:require-macros [macros :as m :refer [debug]])
  (:require [other-ns]
            [my-other-src :as src]))

(defn foo []
  (m/debug :foo (+ 1 2 3)))

(foo)
(debug :foo (+ 1 2 3 4))

(src/debug :dude (+ 1 2 3))
