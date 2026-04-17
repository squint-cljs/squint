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

(deftest per-ns-each-fixtures-test
  (testing "each-fixtures fire only for tests in the matching ns"
    (let [saved-env (t/get-current-env)
          log (atom [])
          mk-fix (fn [tag]
                   (fn [test-fn]
                     (swap! log conj (str tag "-setup"))
                     (test-fn)
                     (swap! log conj (str tag "-teardown"))))
          mk-test (fn [name-str ns-str]
                    (with-meta (fn [] (is true))
                      {:name name-str :ns ns-str}))]
      (t/set-env! (t/empty-env))
      (t/set-each-fixtures! "ns.a" [(mk-fix "a")])
      (t/set-each-fixtures! "ns.b" [(mk-fix "b")])
      (t/test-var (mk-test "t-a" "ns.a"))
      (t/test-var (mk-test "t-b" "ns.b"))
      (t/set-env! saved-env)
      (is (= ["a-setup" "a-teardown" "b-setup" "b-teardown"] @log)
          "each ns sees only its own fixture"))))

(deftest per-ns-once-fixtures-test
  (testing "once-fixtures wrap each ns's tests separately"
    (let [saved-env (t/get-current-env)
          log (atom [])
          mk-fix (fn [tag]
                   (fn [test-fn]
                     (swap! log conj (str tag "-once-setup"))
                     (test-fn)
                     (swap! log conj (str tag "-once-teardown"))))
          mk-test (fn [name-str ns-str]
                    (with-meta (fn [] (swap! log conj name-str) (is true))
                      {:name name-str :ns ns-str}))]
      (t/set-env! (t/empty-env))
      (t/set-once-fixtures! "ns.a" [(mk-fix "a")])
      (t/set-once-fixtures! "ns.b" [(mk-fix "b")])
      (t/run-tests (mk-test "a-1" "ns.a")
                   (mk-test "a-2" "ns.a")
                   (mk-test "b-1" "ns.b"))
      (t/set-env! saved-env)
      (is (= ["a-once-setup" "a-1" "a-2" "a-once-teardown"
              "b-once-setup" "b-1" "b-once-teardown"] @log)
          "each ns's once-fixture wraps only that ns's tests"))))

(defn ^:async -main []
  (t/set-env! (t/empty-env))
  (t/test-var math-test)
  (t/test-var thrown-test)
  (t/test-var expected-failure-test)
  (js-await (t/test-var async-test))
  (t/test-var per-ns-each-fixtures-test)
  (t/test-var per-ns-once-fixtures-test)
  (t/report {:type :summary}))

(-main)
