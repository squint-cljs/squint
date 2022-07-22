(ns import-test-cljs
  (:require ["./exports.mjs" :refer [foo]]))

(prn (foo))

