(ns main
  (:require-macros [macros :refer [with-add-100]])
  (:require [macros :as mac]))

(println (with-add-100 42))
