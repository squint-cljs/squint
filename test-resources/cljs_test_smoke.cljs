(ns cljs-test-smoke
  (:require [cljs.test :as t :refer [deftest is testing are async]]))

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

(deftest run-tests-counter-isolation-test
  (testing "an inner run-tests doesn't disturb the caller's counters"
    (let [before (:report-counters (t/get-current-env))
          inner-result (t/run-tests
                        (with-meta (fn [] (is true)) {:name "inner-1" :ns "x"})
                        (with-meta (fn [] (is true)) {:name "inner-2" :ns "x"}))
          after (:report-counters (t/get-current-env))]
      (is (= before after)
          "outer counters are restored after run-tests returns")
      (is (= 2 (:test inner-result))
          "returned summary reflects only the inner run's tests")
      (is (= 2 (:pass inner-result))
          "returned summary reflects only the inner run's passes"))))

(deftest report-only-counts-pass-fail-error-test
  (testing "non-result :type values don't bump :report-counters"
    (let [saved-env (t/get-current-env)]
      (t/set-env! (t/empty-env))
      (t/report {:type :summary})
      (t/report {:type :begin-test-ns :ns "x"})
      (t/report {:type :end-test-ns :ns "x"})
      (let [counters (:report-counters (t/get-current-env))]
        (t/set-env! saved-env)
        (is (zero? (:test counters)) ":test stays 0")
        (is (zero? (:pass counters)) ":pass stays 0")
        (is (nil? (:summary counters)) "no bogus :summary key added")
        (is (nil? (:begin-test-ns counters))
            "no bogus :begin-test-ns key added")))))

(deftest async-done-form-test
  ;; (async done ...) is the cljs.test idiom — body runs, done resolves
  ;; the wrapping promise, test-var awaits it.
  (async done
    (js/setTimeout
     (fn []
       (is (= 42 (* 6 7)))
       (done))
     5)))

(deftest run-tests-quoted-symbol-test
  (testing "(run-tests 'my.ns) macro converts quoted symbol to a string"
    (let [saved-env (t/get-current-env)]
      (t/register-test! "synthetic.ns"
                        (with-meta (fn [] (is true))
                          {:name "synthetic" :ns "synthetic.ns"}))
      (t/set-env! (t/empty-env))
      (let [result (t/run-tests 'synthetic.ns)]
        (t/set-env! saved-env)
        (is (= 1 (:test result))
            "quoted ns symbol must reach the runtime as a string and resolve")
        (is (= 1 (:pass result))
            "the inner test ran and its assertion passed")))))

(deftest report-is-multimethod-test
  (testing "users can defmethod cljs.test/report to hook reporting events"
    (let [saved-env (t/get-current-env)
          events (atom [])]
      (t/set-env! (t/empty-env))
      (defmethod t/report [:cljs.test/default :begin-test-var] [m]
        (swap! events conj [:begin (:name m)]))
      (defmethod t/report [:cljs.test/default :end-test-var] [m]
        (swap! events conj [:end (:name m)]))
      (t/test-var (with-meta (fn [] (is true))
                    {:name "inner" :ns "smoke"}))
      ;; restore defaults so later tests aren't polluted
      (defmethod t/report [:cljs.test/default :begin-test-var] [_])
      (defmethod t/report [:cljs.test/default :end-test-var] [_])
      (t/set-env! saved-env)
      (is (= [[:begin "inner"] [:end "inner"]] @events)
          "begin/end-test-var events fire and carry the var name"))))

(defn ^:async -main []
  (t/set-env! (t/empty-env))
  (t/test-var math-test)
  (t/test-var thrown-test)
  (t/test-var expected-failure-test)
  (await (t/test-var async-test))
  (await (t/test-var async-done-form-test))
  (t/test-var per-ns-each-fixtures-test)
  (t/test-var per-ns-once-fixtures-test)
  (t/test-var run-tests-counter-isolation-test)
  (t/test-var report-only-counts-pass-fail-error-test)
  (t/test-var run-tests-quoted-symbol-test)
  (t/test-var report-is-multimethod-test)
  (t/report {:type :summary}))

(-main)
