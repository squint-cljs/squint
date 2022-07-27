(ns foo.test
  (:require ["vitest" :refer [describe it expect]]))

(defn ^:async foo-test []
  (-> (expect "foo")
      (.toMatch "foo")))

(describe "suite"
          (fn []
            (it "serial test"
                foo-test)))
