(ns macro-usage
  (:require-macros ["./macros.mjs" :refer [do-twice]]))

(do-twice (prn :hello))
