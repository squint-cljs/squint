(ns cljs-test-smoke
  (:require [cljs.test :as t :refer [deftest is testing are]]))

(deftest math-test
  (testing "basic math"
    (is (= 4 (+ 2 2)))
    (are [x y sum] (= sum (+ x y))
      1 1 2
      2 3 5
      10 20 30)))

(deftest thrown-test
  (is (thrown? js/Error (throw (js/Error. "boom"))))
  (is (thrown-with-msg? js/Error #"boom"
        (throw (js/Error. "boom!")))))

(deftest expected-failure-test
  (is (= :foo :bar) "intentional"))

(deftest ^:async async-test
  (js/Promise.
   (fn [resolve]
     (js/setTimeout
      (fn []
        (is (= 42 (* 6 7)))
        (resolve))
      5))))

(defn ^:async -main []
  (t/set-env! (t/empty-env))
  (t/test-var math-test)
  (t/test-var thrown-test)
  (t/test-var expected-failure-test)
  (js-await (t/test-var async-test))
  (t/report {:type :summary}))

(-main)
