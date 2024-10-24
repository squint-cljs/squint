(ns dummy-test
  (:require ["vitest" :refer [expect test]]))

(test "dummy expect works"
      (fn []
        (-> (expect 1)
            (.toBe 1))))
