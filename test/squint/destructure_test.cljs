(ns squint.destructure-test
  "Clojure 1.13 destructuring. Ported from sci's test/sci/destructure_test.cljc
  (sci c0c50774), adapted to squint's data model: a keyword, a symbol and a
  string all munge to the same map key, so :keys, :syms and :strs are one
  directive here. See ident-types-collapse-test."
  (:require
   [clojure.test :as t :refer [deftest is testing]]
   [squint.test-utils :refer [jsv!]]))

(deftest req!-test
  (testing "req! returns the value, or throws when the key is absent"
    (is (= 1 (jsv! "(req! {:a 1 :b 2} :a)")))
    (is (= 2 (jsv! "(req! {:a 1 :b 2} :b)")))
    (is (nil? (jsv! "(req! {:f nil} :f)")))
    (is (false? (jsv! "(req! {:g false} :g)")))
    (is (thrown? js/Error (jsv! "(req! {:a 1} :e)"))))
  (testing "lookup follows get, not just maps"
    (is (thrown? js/Error (jsv! "(req! nil :a)")))
    (is (= 2 (jsv! "(req! [1 2] 1)")))
    (is (thrown? js/Error (jsv! "(req! [1 2] 5)")))
    (is (= "a" (jsv! "(req! #{:a} :a)")))
    (is (thrown? js/Error (jsv! "(req! #{:a} :b)")))))

(deftest some-vals-test
  (is (true? (jsv! "(= {:a 1} (some-vals {:a 1 :b nil}))")))
  (is (nil? (jsv! "(some-vals {:a nil})")))
  (is (nil? (jsv! "(some-vals nil)")))
  (is (true? (jsv! "(= {:a false} (some-vals {:a false}))"))))

(deftest keys-bang-test
  (testing ":keys! binds and throws when a key is missing"
    (is (= 1 (jsv! "(let [{:keys! [a b]} {:a 1 :b 2}] a)")))
    (is (thrown? js/Error (jsv! "(let [{:keys! [a b]} {:a 1}] a)"))))
  (testing ":keys! with & requires keys after & without binding them"
    (is (= 1 (jsv! "(let [{:keys! [a & :b]} {:a 1 :b 2}] a)")))
    (is (thrown? js/Error (jsv! "(let [{:keys! [a & :b]} {:a 1}] a)"))))
  (testing "nested maps with :keys! &"
    (is (true? (jsv! "(let [m {:a 1 :b {:a 2 :b 3 :c 4 :d 42}}]
                       (let [{a :a {aa :a :as mm :keys! [b c & :d]} :b} m]
                         (= [(:b m) 1 2 3 4] [mm a aa b c])))")))
    (is (thrown? js/Error (jsv! "(let [{a :a {:keys! [b c & :d :e]} :b}
                                       {:a 1 :b {:a 2 :b 3 :c 4 :d 42}}] a)")))
    (is (thrown? js/Error (jsv! "(let [{a :a {:keys! [b c & :d]} :b}
                                       {:a 1 :b {:a 2 :b 3 :d 42}}] a)"))))
  (testing "qualified names and declarators with :keys! &"
    (is (= 1 (jsv! "(let [{:keys! [foo/a & :b]} {:foo/a 1 :b 2}] a)")))
    (is (= 2 (jsv! "(let [{:keys! [b & :foo/c]} {:foo/a 1 :b 2 :foo/c 3}] b)")))
    (is (thrown? js/Error (jsv! "(let [{:keys! [b & :foo/c]} {:foo/a 1 :foo/c 3}] b)")))
    (is (thrown? js/Error (jsv! "(let [{:keys! [b & :foo/c]} {:foo/a 1 :b 2}] b)")))
    (is (= 1 (jsv! "(let [{:foo/keys! [aa & :bb]} {:foo/aa 1 :bb 2}] aa)"))))
  (testing "keys after & are not bound"
    (is (thrown? js/Error (jsv! "(let [{:keys! [a & :b]} {:a 1 :b 2}] b)"))))
  (testing "& may appear only once"
    (is (thrown? js/Error (jsv! "(let [{:keys [a & :b & :c]} {:a 1}] a)")))))

(deftest ident-types-collapse-test
  (testing "a keyword, a symbol and a string munge to one map key, so :keys,
            :syms and :strs are interchangeable (squint divergence)"
    (is (true? (jsv! "(= (keys {:a 1}) (keys '{a 1}) (keys {\"a\" 1}))")))
    (is (= 1 (jsv! "(let [{:syms [a]} {:a 1}] a)")))
    (is (= 1 (jsv! "(let [{:strs [a]} {:a 1}] a)")))
    (is (= 1 (jsv! "(let [{:syms! [a]} {:a 1}] a)")))
    (is (= 1 (jsv! "(let [{:strs! [a]} {:a 1}] a)")))
    (is (thrown? js/Error (jsv! "(let [{:syms! [a]} {:b 1}] a)")))
    (is (thrown? js/Error (jsv! "(let [{:strs! [a]} {:b 1}] a)")))))

(deftest mixed-keys-after-amp-test
  (is (= 1 (jsv! "(let [{:keys [a & 'b]} {:a 1}] a)")))
  (is (= 1 (jsv! "(let [{:keys! [a & 'b \"c\"]} {:a 1 'b 2 \"c\" 3}] a)")))
  (is (thrown? js/Error (jsv! "(let [{:keys! [a & 'b \"c\"]} {:a 1 'b 2}] a)"))))

(deftest select-test
  (testing "select picks up keys mentioned anywhere in the binding form"
    (is (true? (jsv! "(let [{:keys [a b & :c] :keys! [d] :select sel}
                            {:a 1 :b 2 :c 3 :d 4 :e 5}]
                       (= {:a 1 :b 2 :c 3 :d 4} sel))")))
    (is (true? (jsv! "(let [{:foo/keys [x & :zz] :foo/keys! [z] :select sel}
                            {:foo/x 1000 :foo/y 2000 :foo/z 3000}]
                       (= {:foo/x 1000 :foo/z 3000} sel))"))))
  (testing "select descends into nested maps"
    (is (true? (jsv! "(let [{{aa :aa} :nested :select sel}
                            {:nested {:aa 1 :bb 2} :c 3}]
                       (= {:nested {:aa 1}} sel))"))))
  (testing "select of everything equals :as"
    (is (true? (jsv! "(let [m {:a 1 :b 2}
                            {:keys [a b] :as mm :select sel} m]
                       (= sel mm))"))))
  (testing "select doesn't fabricate maps"
    (is (nil? (jsv! "(let [{{a :a} :n :select s} nil] s)")))
    (is (true? (jsv! "(= {} (let [{{a :a} :n :select s} {}] s))")))
    (is (true? (jsv! "(= {:n nil} (let [{{a :a} :n :select s} {:n nil}] s))")))
    (is (true? (jsv! "(= {:n {}} (let [{{a :a} :n :select s} {:n {}}] s))"))))
  (testing "defaults fill in missing keys"
    (is (true? (jsv! "(= {:n {:a 42}} (let [{{a :a :or {a 42}} :n :select s} nil] s))")))
    (is (true? (jsv! "(= {:n {:a 42}} (let [{{a :a :or {a 42}} :n :select s} {:n nil}] s))")))))

(deftest all-test
  (is (true? (jsv! "(= {:a 1 :b 2} (let [{:keys [a] :all m} {:a 1 :b 2}] m))")))
  (testing ":all keeps keys not mentioned in the binding form"
    (is (true? (jsv! "(= {:n {:aa 1 :bb 2} :c 3}
                         (let [{{aa :aa} :n :all m} {:n {:aa 1 :bb 2} :c 3}] m))"))))
  (testing ":all is augmented by defaults"
    (is (true? (jsv! "(= {:a 42 :b 2} (let [{:keys [a] :or {a 42} :all m} {:b 2}] m))"))))
  (testing ":select and :all in the same binding form"
    (is (true? (jsv! "(let [{:keys [a] :select s :all m} {:a 1 :b 2}]
                       (and (= {:a 1} s) (= {:a 1 :b 2} m)))")))))

(deftest defaults-test
  (testing ":defaults binds a map of key to default value"
    (is (true? (jsv! "(= {} (let [{:defaults d :or {}} {}] d))")))
    (is (true? (jsv! "(= {:a 1} (let [{:keys [a] :defaults d :or {:a 1}} {}] d))")))
    (is (true? (jsv! "(= {:a 1} (let [{:keys [a] :defaults d :or {a 1}} {}] d))"))))
  (testing ":defaults without :or is an error"
    (is (thrown? js/Error (jsv! "(let [{:defaults d} {}] d)"))))
  (testing "the same key can't have both a binding and a key default"
    (is (thrown? js/Error (jsv! "(let [{:keys [a] :defaults d :or {:a 1 a 1}} {}] d)")))))

(deftest or-by-key-test
  (testing ":or accepts key -> val in addition to binding -> val"
    (is (true? (jsv! "(= [1 42] (let [{:keys [a b] :or {:b 42}} {:a 1}] [a b]))")))))

(deftest or-strictness-test
  (testing "with :select, :all or :defaults every :or entry must be a bound key"
    (is (thrown? js/Error (jsv! "(let [{:keys [a] :or {z 42} :select s} {:a 1}] s)")))
    (is (thrown? js/Error (jsv! "(let [{:keys [a & :b] :or {b 42} :select s} {:a 1}] s)")))
    (is (thrown? js/Error (jsv! "(let [{:keys [a] :or {:z 42} :all m} {:a 1}] m)")))
    (is (thrown? js/Error (jsv! "(let [{:defaults d :or {:a 1}} {}] d)")))
    (is (thrown? js/Error (jsv! "(let [{:keys [a] :or {:a 1 :z 2} :select s} {:a 1}] s)"))))
  (testing "without the new directives :or is unchecked"
    (is (= 1 (jsv! "(let [{:keys [a] :or {z 42}} {:a 1}] a)")))))

(deftest required-key-default-test
  (is (thrown? js/Error (jsv! "(let [{:keys! [a] :or {a 1}} {}] a)"))))

(deftest unsupported-map-directive-test
  (is (thrown? js/Error (jsv! "(let [{:vals [a]} {:a 1}] a)"))))

(deftest or-default-refers-to-sibling-binding-test
  (is (= "Does not conform to int"
         (jsv! "(let [{:keys [pred message]
                       :or {message (str \"Does not conform to \" pred)}}
                      {:pred :int}]
                  message)")))
  (testing ":defaults, :select and :all still evaluate a default once"
    (is (true? (jsv! "(let [n (atom 0) f (fn [] (swap! n inc) 42)
                           {:keys [a] :or {a (f)} :defaults d} {}]
                       (and (= 42 a) (= {:a 42} d) (= 1 @n)))")))))

(deftest binding-contexts-test
  (testing "fn params"
    (is (true? (jsv! "(= [1 2] ((fn [{:keys! [a b]}] [a b]) {:a 1 :b 2}))")))
    (is (thrown? js/Error (jsv! "((fn [{:keys! [a b]}] [a b]) {:a 1})")))
    (is (true? (jsv! "(= {:a 1} ((fn [{:keys [a] :select s}] s) {:a 1 :b 2}))"))))
  (testing "kwargs"
    (is (= 1 (jsv! "((fn [& {:keys! [a]}] a) :a 1)")))
    (is (thrown? js/Error (jsv! "((fn [& {:keys! [a]}] a) :b 1)"))))
  (testing "for"
    (is (true? (jsv! "(= [1 2] (vec (for [{:keys! [a]} [{:a 1} {:a 2}]] a)))")))
    (is (thrown? js/Error (jsv! "(vec (for [{:keys! [a]} [{:a 1} {:b 2}]] a))")))
    (is (true? (jsv! "(= [{:a 1}] (vec (for [{:keys [a] :select s} [{:a 1 :b 2}]] s)))"))))
  (testing "loop"
    (is (true? (jsv! "(= {:a 1 :b 2} (loop [{:keys [a] :all m} {:a 1 :b 2}] m))")))))
