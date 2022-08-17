(ns clava.compiler-test
  (:require
   ["clavascript/core.js" :as cl]
   ["lodash$default" :as ld]
   [clava.compiler :as clava]
   [clojure.string :as str]
   [clojure.test :as t :refer [async deftest is testing]]))

(doseq [k (js/Object.keys cl)]
  (aset js/globalThis k (aget cl k)))

(defn eq [a b]
  (ld/isEqual (clj->js a) (clj->js b)))

(def old-fail (get-method t/report [:cljs.test/default :fail]))

(defmethod t/report [:cljs.test/default :fail] [m]
  (set! js/process.exitCode 1)
  (old-fail m))

(def old-error (get-method t/report [:cljs.test/default :fail]))

(defmethod t/report [:cljs.test/default :error] [m]
  (set! js/process.exitCode 1)
  (old-error m))

(defn jss! [expr]
  (if (string? expr)
    (:body (clava/compile-string* expr))
    (clava/transpile-form expr)))

(defn js! [expr]
  (let [js (jss! expr)]
    [(js/eval js) js]))

(defn jsv! [expr]
  (first (js! expr)))

(deftest return-test
  (is (str/includes? (jss! '(do (def x (do 1 2 nil))))
                     "return"))
  (is (str/includes? (jss! '(do (def x (do 1 2 "foo"))))
                     "return"))
  (is (str/includes? (jss! '(do (def x (do 1 2 :foo))))
                     "return"))
  (is (str/includes? (jss! "(do (def x (do 1 2 \"hello\")))")
                     "return"))
  (let [s (jss! "(do (def x (do 1 2 [1 2 3])) x)")]
    (is (eq #js [1 2 3] (js/eval s))))
  (let [s (jss! "(do (def x (do 1 2 {:x 1 :y 2})) x)")]
    (is (eq #js {:x 1 :y 2} (js/eval s))))
  (let [s (jss! "(do (def x (do 1 2 #js {:x 1 :y 2})) x)")]
    (is (eq (str #js {:x 1 :y 2}) (str (js/eval s))))))

(deftest do-test
  (let [[v s] (js! '(do 1 2 3))]
    (is (= 3 v))
    (is (not (str/includes? s "function"))))
  (let [[v s] (js! '(do 1 2 3 (do 4 5 6)))]
    (is (= 6 v))
    (is (not (str/includes? s "function"))))
  (let [[v s] (js! '(do (def x (do 4 5 6))
                        x))]
    (is (= 6 v))
    (is (str/includes? s "function")))
  (let [[v s] (js! '(let [x (do 4 5 6)]
                      x))]
    (is (= 6 v))
    (is (str/includes? s "function"))))

(deftest let-test
  (is (= 3 (jsv! '(let [x (do 1 2 3)] x))))
  (is (= 3 (jsv! '(let [x 1 x (+ x 2)] x))))
  (let [s (jss! '(let [x 1 x (let [x (+ x 1)]
                               x)] x))]
    (is (= 2 (js/eval s))))
  (is (= 7 (jsv! '(let [{:keys [a b]} {:a 1 :b (+ 1 2 3)}]
                    (+ a b)))))
  (is (= 8 (jsv!
            '(+ 1
                (let [{:keys [a b]} {:a 1 :b (+ 1 2 3)}]
                  (+ a b)))))))

(deftest let-interop-test
  (is (= "f" (jsv! '(let [x "foo"]
                      (.substring x 0 1)))))
  (is (= 3 (jsv! '(let [x "foo"]
                    (.-length x))))))

(deftest let-shadow-test
  (is (= 1 (jsv! '(let [name 1]
                    name))))
  (is (= 1 (jsv! '(let [name (fn [] 1)]
                    (name)))))
  (let [s (jss! '(let [name (fn [_] 1)]
                   (map name [1 2 3])))]
    (is (eq #js [1 1 1]
            (js/eval s))))
  (let [s (jss! '(let [name (fn [_] 1)
                       name (fn [_] 2)]
                   (map name [1 2 3])))]
    (is (eq #js [2 2 2]
            (js/eval s)))))

(deftest destructure-test
  (let [s (jss! "(let [^js {:keys [a b c]} #js {:a 1 :b 2 :c 3}]
                   (+ a b c))")]
    (is (= 6 (js/eval s)))))

(deftest fn-test
  (let [s (jss! '(let [f (fn [x] x)]
                   f))]
    (is (= 1 ((js/eval s) 1))))
  (let [s (jss! '(let [f (fn [x] 1 2 x)]
                   f))]
    (is (= 1 ((js/eval s) 1))))
  (let [s (jss! '(let [f (fn [x] 1 2 (+ 1 x))]
                   f))]
    (is (= 2 ((js/eval s) 1))))
  (let [s (jss! '(let [f (fn [x] 1 2 (do 1 x))]
                   f))]
    (is (= 1 ((js/eval s) 1))))
  (is (= 1 (jsv! '(do (defn foo [] (fn [x] x)) ((foo) 1))))))

(deftest fn-varargs-test
  (is (eq #js [3 4] (jsv! '(let [f (fn foo [x y & zs] zs)] (f 1 2 3 4)))))
  (is (nil? (jsv! '(let [f (fn foo [x y & zs] zs)] (f 1 2))))))

(deftest fn-multi-arity-test
  (is (= 1 (jsv! '(let [f (fn foo ([x] x) ([x y] y))] (f 1)))))
  (is (= 2 (jsv! '(let [f (fn foo ([x] x) ([x y] y))] (f 1 2))))))

(deftest fn-multi-varargs-test
  (is (= 1 (jsv! '(let [f (fn foo ([x] x) ([x y & zs] zs))] (f 1)))))
  (is (eq '(3 4) (jsv! '(let [f (fn foo ([x] x) ([x y & zs] zs))] (f 1 2 3 4)))))
  (is (nil? (jsv! '(let [f (fn foo ([x] x) ([x y & zs] zs))] (f 1 2))))))

(deftest defn-test
  (let [s (jss! '(do (defn f [x] x) f))]
    (is (= 1 ((js/eval s) 1))))
  (let [s (jss! '(do (defn f [x] (let [y 1] (+ x y))) f))]
    (is (= 2 ((js/eval s) 1))))
  (let [s (jss! '(do (defn foo [x]
                       (dissoc! x :foo))
                     (foo {:a 1 :foo :bar})))]
    (is (eq {:a 1} (js/eval s))))
  (let [s (jss! "(do (defn f [^js {:keys [a b c]}] (+ a b c)) f)")]
    (is (= 6 ((js/eval s) #js {:a 1 :b 2 :c 3}))))
  (let [s (jss! '(do (defn quux [x]
                       (if (= 1 1)
                         1
                         2))
                     (quux 1)))]
    (is (= 1 (js/eval s)))))

(deftest defn-multi-arity-test
  (is (= 1 (jsv! '(do
                    (defn foo ([x] x) ([x y] y))
                    (foo 1)))))
  (is (= 2 (jsv! '(do
                    (defn foo ([x] 1) ([x y] y))
                    (foo 1 2))))))

(deftest defn-recur-test
  (let [s (jss! '(do (defn quux [x]
                       (if (> x 0)
                         (recur (dec x))
                         x))
                     (quux 1)))]
    (is (zero? (js/eval s)))))

(deftest defn-varargs-test
  (let [s (jss! '(do (defn foo [x & args] args) (foo 1 2 3)))]
    (is (eq '(2 3) (js/eval s)))))

(deftest defn-multi-varargs-test
  (is (eq [1 [1 2 '(3 4)]]
          (js/eval
           (jss! '(do (defn foo
                        ([x] x)
                        ([x y & args]
                         [x y args]))
                      [(foo 1) (foo 1 2 3 4)]))))))

(deftest loop-test
  (let [s (jss! '(loop [x 1] (+ 1 2 x)))]
    (is (= 4 (js/eval s))))
  (let [s (jss! '(loop [x 10]
                   (if (> x 0)
                     (recur (dec x))
                     x)))]
    (is (zero? (js/eval s)))))

(deftest if-test
  (is (false? (jsv! "(if 0 true false)")))
  (let [s (jss! "[(if false true false)]")]
    (false? (first (js/eval s))))
  (let [s (jss! "(let [x (if (inc 1) (inc 2) (inc 3))]
                   x)")]
    (is (= 3 (js/eval s))))
  (let [s (jss! "(let [x (do 1 (if (inc 1) (inc 2) (inc 3)))]
                   x)")]
    (is (= 3 (js/eval s)))))

(deftest doseq-test
  (let [s (jss! '(let [a []]
                   (doseq [x [1 2 3]]
                     (.push a x))
                   a))]
    (is (eq [1 2 3] (js/eval s))))
  ;; TODO:
  #_(let [s (jss! '(let [a []]
                     (doseq [x [1 2 3]
                             y [4 5 6]]
                       (.push a x))
                     a))]
      (println s)
      (is (eq [1 4 1 5 1 6 2 4 2 5 2 6 3 4 3 5 3 6]
              (js/eval s)))))

;; TODO:
#_(deftest for-test
    (let [s (jss! '(for [x [1 2 3] y [4 5 6]] [x y]))]
      (is (= '([1 4] [1 5] [1 6] [2 4] [2 5] [2 6] [3 4] [3 5] [3 6])
             (js/eval s)))))

(deftest regex-test
  (is (eq '("foo")
          (jsv! '(.match "foo foo" #"foo")))))

(deftest new-test
  (is (eq "hello" (jsv! '(str (js/String. "hello"))))))

(deftest quote-test
  (is (eq '{x 1} (jsv! (list 'quote '{x 1})))))

(deftest case-test
  (is (= 2 (jsv! '(case 1 1 2 3 4))))
  (is (= 5 (jsv! '(case 6 1 2 3 4 (inc 4)))))
  (is (= 2 (jsv! '(case 1 :foo :bar 1 2))))
  (is (= "bar" (jsv! '(case :foo :foo :bar))))
  (let [s (jss! '(let [x (case 1 1 2 3 4)]
                   (inc x)))]
    (is (= 3 (js/eval s))))
  (let [s (jss! '(do (defn foo []
                       (case 1 1 2 3 4))
                     (foo)))]
    (is (= 2 (js/eval s)))))

(deftest dot-test
  (let [s (jss! "(do (def x (.-x #js {:x 1})) x)")]
    (is (= 1 (js/eval s))))
  (let [s (jss! "(do (def x (. #js {:x 1} -x)) x)")]
    (is (= 1 (js/eval s))))
  (let [s (jss! "(do (def x (.x #js {:x (fn [] 1)})) x)")]
    (is (= 1 (js/eval s))))
  (let [s (jss! "(do (def x (.x #js {:x (fn [x] x)} 1)) x)")]
    (is (= 1 (js/eval s))))
  (let [s (jss! "(do (def x (. #js {:x (fn [x] x)} x 1)) x)")]
    (is (= 1 (js/eval s))))
  (let [s (jss! "(do (def x (. #js {:x (fn [x] x)} (x 1))) x)")]
    (is (= 1 (js/eval s))))
  (let [s (jss! "(.goto #js {:goto (fn [x] [:hello x])} 10)")]
    (is (eq [:hello 10] (js/eval s)))))

(deftest dotdot-test
  (let [s (jss! "(.. #js {:foo #js {:bar 2}} -foo -bar)")]
    (is (= 2 (js/eval s)))))

#_(js-delete js/require.cache (js/require.resolve "/tmp/debug.js"))
#_(js/require "/tmp/debug.js")

#_(deftest backtick-test
    (is (= '(assoc {} :foo :bar) (jsv! "`(assoc {} :foo :bar)"))))

#_(deftest munged-core-name-test
    (is (jsv! '(boolean 1))))

(deftest defprotocol-extend-type-string-test
  (is (eq "foo" (jsv! '(do (defprotocol IFoo (foo [_])) (extend-type string IFoo (foo [_] :foo)) (foo "bar"))))))

(deftest deftype-test
  (is (= 1 (jsv! '(do (deftype Foo [x]) (.-x (->Foo 1))))))
  (is (eq [:foo :bar]
          (jsv! '(do
                   (defprotocol IFoo (foo [_]) (bar [_]))
                   (deftype Foo [x] IFoo (foo [_] :foo)
                            (bar [_] :bar))
                   (let [x (->Foo 1)]
                     [(foo x) (bar x)])))))
  (is (eq [:foo 2]
          (jsv! '(do (defprotocol IFoo (foo [_]) (bar [_]))
                     (deftype Foo [^:mutable x]
                       IFoo
                       (foo [_] [:foo x])
                       (bar [_] :bar))
                     (def x  (->Foo 1))
                     (set! (.-x x) 2)
                     (foo x))))))

(deftest set-test
  (is (ld/isEqual (js/Set. #js [1 2 3]) (jsv! #{1 2 3}))))

(deftest await-test
  (async done
         (.then  (jsv! '(do (defn ^:async foo []
                              (js/await (js/Promise.resolve :hello)))

                            (defn ^:async bar []
                              (let [x (js/await (foo))]
                                x))

                            (bar)))
                 (fn [v]
                   (is (eq :hello v))
                   (done)))))

(deftest native-js-array-test
  (let [s (jss! "(let [x 2
                       x #js [1 2 x]]
                   x)")
        x (js/eval s)]
    (is (array? x))
    (is (= [1 2 2] (js->clj x))))
  (is (= 1 (jsv! "(aget  #js [1 2 3] 0)"))))

(deftest keyword-call-test
  (is (= "bar" (jsv! '(:foo {:foo :bar})))))

(deftest minus-single-arg-test
  (is (= -10 (jsv! '(- 10))))
  (is (= -11 (jsv! '(- 10 21)))))

(deftest namespace-keywords
  (is (eq #js {"foo/bar" "baz"} (jsv! {:foo/bar :baz})))
  (is (eq "hello/world" (jsv! "(ns hello) ::world"))))

(deftest pr-str-test
  (is (eq (js/Set. #js ["a" "b" "c"]) (js/Set. (js/JSON.parse (jsv! '(pr-str #{:a :b :c})))))))

(deftest str-test
  (is (eq "123" (jsv! '(str 1 2 3))))
  (is (eq "foobarbaz", (jsv! '(str "foo" "bar" "baz"))))
  (is (eq "1barfirst,second[object Object]"
          (jsv! '(str 1 "bar" [:first :second] {"hello" "goodbye"})))))

(deftest conj-test
  (testing "corner cases"
    (is (eq [], (jsv! '(conj))))
    (is (eq [], (jsv! '(conj nil))))
    (is (eq [1 2], (jsv! '(conj nil 1 2)))))
  (testing "arrays"
    (is (eq [1 2 3 4] (jsv! '(conj [1 2 3 4]))))
    (is (eq [1 2 3 4] (jsv! '(conj [1 2 3] 4))))
    (is (eq [1 2 3 4] (jsv! '(conj [1 2] 3 4)))))
  (testing "sets"
    (is (eq (js/Set. #js [1 2 3 4]) (jsv! '(conj #{1 2 3 4}))))
    (is (eq (js/Set. #js [1 2 3 4]) (jsv! '(conj #{1 2 3} 4))))
    (is (eq (js/Set. #js [1 2 3 4]) (jsv! '(conj #{1 2} 3 4)))))
  (testing "objects"
    (is (eq #js {:a "b" :c "d"} (jsv! '(conj {:a "b" :c "d"}))))
    (is (eq #js {"1" 2 "3" 4} (jsv! '(conj {"1" 2} ["3" 4]))))
    (is (eq #js {"1" 2 "3" 4 "5" 6} (jsv! '(conj {"1" 2} ["3" 4] ["5" 6])))))
  (testing "maps"
    (is (eq (js/Map. #js [#js ["a" "b"] #js ["c" "d"]])
            (jsv! '(conj (js/Map. [["a" "b"] ["c" "d"]])))))
    (is (eq (js/Map. #js [#js [1 2] #js [3 4]])
            (jsv! '(conj (js/Map. [[1 2]]) [3 4]))))
    (is (eq (js/Map. #js [#js [1 2] #js [3 4] #js [5 6]])
            (jsv! '(conj (js/Map. [[1 2]]) [3 4] [5 6])))))
  (testing "other types"
    (is (thrown? js/Error (jsv! '(conj "foo"))))))

(deftest conj!-test
  (testing "corner cases"
    (is (eq [], (jsv! '(conj!))))
    (is (eq [], (jsv! '(conj! nil))))
    (is (eq [1 2], (jsv! '(conj! nil 1 2)))))
  (testing "arrays"
    (is (eq [1 2 3 4] (jsv! '(conj! [1 2 3 4]))))
    (is (eq [1 2 3 4] (jsv! '(conj! [1 2 3] 4))))
    (is (eq [1 2 3 4] (jsv! '(let [x [1 2 3]]
                               (conj! x 4)
                               x))))
    (is (eq [1 2 3 4] (jsv! '(conj! [1 2] 3 4))))
    (is (eq [1 2 3 4] (jsv! '(let [x [1 2]]
                               (conj! x 3 4)
                               x)))))
  (testing "sets"
    (is (eq (js/Set. #js [1 2 3 4]) (jsv! '(conj! #{1 2 3 4}))))
    (is (eq (js/Set. #js [1 2 3 4]) (jsv! '(conj! #{1 2 3} 4))))
    (is (eq (js/Set. #js [1 2 3 4]) (jsv! '(let [x #{1 2 3}]
                                             (conj! x 4)
                                             x))))
    (is (eq (js/Set. #js [1 2 3 4]) (jsv! '(conj! #{1 2} 3 4))))
    (is (eq (js/Set. #js [1 2 3 4]) (jsv! '(let [x #{1 2}]
                                             (conj! x 3 4)
                                             x)))))
  (testing "objects"
    (is (eq #js {:a "b" :c "d"} (jsv! '(conj! {:a "b" :c "d"}))))
    (is (eq #js {"1" 2 "3" 4} (jsv! '(conj! {"1" 2} ["3" 4]))))
    (is (eq #js {"1" 2 "3" 4} (jsv! '(let [x {"1" 2}]
                                       (conj! x ["3" 4])
                                       x))))
    (is (eq #js {"1" 2 "3" 4 "5" 6} (jsv! '(conj! {"1" 2} ["3" 4] ["5" 6]))))
    (is (eq #js {"1" 2 "3" 4 "5" 6} (jsv! '(let [x {"1" 2}]
                                             (conj! x ["3" 4] ["5" 6])
                                             x)))))
  (testing "maps"
    (is (eq (js/Map. #js [#js ["a" "b"] #js ["c" "d"]])
            (jsv! '(conj! (js/Map. [["a" "b"] ["c" "d"]])))))
    (is (eq (js/Map. #js [#js [1 2] #js [3 4]])
            (jsv! '(conj! (js/Map. [[1 2]]) [3 4]))))
    (is (eq (js/Map. #js [#js [1 2] #js [3 4]])
            (jsv! '(let [x (js/Map. [[1 2]])]
                     (conj! x [3 4])))))
    (is (eq (js/Map. #js [#js [1 2] #js [3 4] #js [5 6]])
            (jsv! '(conj! (js/Map. [[1 2]]) [3 4] [5 6]))))
    (is (eq (js/Map. #js [#js [1 2] #js [3 4] #js [5 6]])
            (jsv! '(let [x (js/Map. [[1 2]])]
                     (conj! x [3 4] [5 6])
                     x)))))
  (testing "other types"
    (is (thrown? js/Error (jsv! '(conj! "foo"))))))

(deftest assoc-test
  (testing "arrays"
    (is (eq [1 2 8 4] (jsv! '(assoc [1 2 3 4] 2 8))))
    (is (eq [6 2 8 4] (jsv! '(assoc [1 2 3 4] 2 8 0 6)))))
  (testing "objects"
    (is (eq #js {"1" 2 "3" 4} (jsv! '(assoc {"1" 2} "3" 4))))
    (is (eq #js {"1" 2 "3" 4 "5" 6} (jsv! '(assoc {"1" 2} "3" 4 "5" 6)))))
  (testing "maps"
    (is (eq (js/Map. #js [#js [1 2] #js [3 4]])
            (jsv! '(assoc (js/Map. [[1 2]]) 3 4))))
    (is (eq (js/Map. #js [#js [1 2] #js [3 4] #js [5 6]])
            (jsv! '(assoc (js/Map. [[1 2]]) 3 4 5 6)))))
  (testing "other types"
    (is (thrown? js/Error (jsv! '(assoc! "foo" 1 2))))))

(deftest assoc!-test
  (testing "arrays"
    (is (eq [1 2 8 4] (jsv! '(assoc! [1 2 3 4] 2 8))))
    (is (eq [1 2 8 4] (jsv! '(let [x [1 2 3 4]]
                               (assoc! x 2 8)
                               x))))
    (is (eq [6 2 8 4] (jsv! '(assoc! [1 2 3 4] 2 8 0 6))))
    (is (eq [6 2 8 4] (jsv! '(let [x [1 2 3 4]]
                               (assoc! x 2 8 0 6)
                               x)))))
  (testing "objects"
    (is (eq #js {"1" 2 "3" 4} (jsv! '(assoc! {"1" 2} "3" 4))))
    (is (eq #js {"1" 2 "3" 4} (jsv! '(let [x {"1" 2}]
                                       (assoc! x "3" 4)
                                       x))))
    (is (eq #js {"1" 2 "3" 4 "5" 6} (jsv! '(assoc! {"1" 2} "3" 4 "5" 6))))
    (is (eq #js {"1" 2 "3" 4 "5" 6} (jsv! '(let [x {"1" 2}]
                                             (assoc! x "3" 4 "5" 6)
                                             x)))))
  (testing "maps"
    (is (eq (js/Map. #js [#js [1 2] #js [3 4]])
            (jsv! '(assoc! (js/Map. [[1 2]]) 3 4))))
    (is (eq (js/Map. #js [#js [1 2] #js [3 4]])
            (jsv! '(let [x (js/Map. [[1 2]])]
                     (assoc! x 3 4)
                     x))))
    (is (eq (js/Map. #js [#js [1 2] #js [3 4] #js [5 6]])
            (jsv! '(assoc! (js/Map. [[1 2]]) 3 4 5 6))))
    (is (eq (js/Map. #js [#js [1 2] #js [3 4] #js [5 6]])
            (jsv! '(let [x (js/Map. [[1 2]])]
                     (assoc! x 3 4 5 6)
                     x)))))
  (testing "other types"
    (is (thrown? js/Error (jsv! '(assoc! "foo" 1 2))))))

(deftest assoc-in-test
  (testing "happy path"
    (is (eq #js {"1" 3}
            (jsv! '(assoc-in {"1" 2} ["1"] 3))))
    (is (eq #js {"1" #js [(js/Map. #js [#js [8 9]])]}
            (jsv! '(assoc-in {"1" [(js/Map. [[8 5]])]}
                             ["1" 0 8]
                             9))))
    (is (eq true (jsv! '(let [x (js/Map. [[8 5]])
                              y [x]
                              z {"1" y}
                              z* (assoc-in z ["1" 0 8] 9)
                              y* (get z* "1")
                              x* (get y* 0)]
                          (and (not= x y*)
                               (not= y y*)
                               (not= z z*))))))
    (is (eq {:foo {:bar :baz}} (jsv! (assoc-in {} [:foo :bar] :baz)))))
  (testing "invalid data in path"
    (is (thrown? js/Error (jsv! '(assoc-in "foo" [0] 2))))
    (is (eq #js {"0" #js {"1" 2}, "1" "foo"} (jsv! '(assoc-in {"1" "foo"} [0 1] 2))))))

(deftest assoc-in!-test
  (testing "happy path"
    (is (eq #js {"1" 3}
            (jsv! '(let [x {"1" 2}]
                     (assoc-in! x ["1"] 3)
                     x))))
    (is (eq true (jsv! '(let [x (js/Map. [[8 5]])
                              y [x]
                              z {"1" y}
                              z* z]
                          (assoc-in! z ["1" 0 8] 9)
                          (and (= x (get (get z "1") 0))
                               (= y (get z "1"))
                               (= z z*)))))))
  (is (eq #js {"0" #js {"1" 2}, "1" "foo"} (jsv! '(assoc-in! {"1" "foo"} [0 1] 2))))
  (testing "invalid data in path"
    (is (thrown? js/Error (jsv! '(assoc-in! "foo" [0] 2))))))

(deftest get-test
  (testing "maps"
    (is (eq 1 (jsv! '(get (js/Map. [["my-key" 1]]) "my-key"))))
    (is (eq nil (jsv! '(get (js/Map. [["my-key" 1]]) "bad-key"))))
    (is (eq 3 (jsv! '(get (js/Map. [["my-key" 1]]) "bad-key" 3)))))
  (testing "arrays"
    (is (eq "val2" (jsv! '(get ["val1" "val2" "val3"] 1))))
    (is (eq nil (jsv! '(get ["val1" "val2" "val3"] 10))))
    (is (eq "val2" (jsv! '(get ["val1" "val2" "val3"] 10 "val2")))))
  (testing "objects"
    (is (eq 1 (jsv! '(get {"my-key" 1} "my-key"))))
    (is (eq nil (jsv! '(get {"my-key" 1} "bad-key"))))
    (is (eq 3 (jsv! '(get {"my-key" 1} "bad-key" 3))))))

(deftest first-test
  (is (= nil (jsv! '(first nil))))
  (is (= nil (jsv! '(first []))))
  (is (= nil (jsv! '(first #{}))))
  (is (= nil (jsv! '(first {}))))
  (is (= nil (jsv! '(first (js/Map. [])))))
  (is (= 1 (jsv! '(first [1 2 3]))))
  (is (= 1 (jsv! '(first #{1 2 3}))))
  (is (eq #js [1 2] (jsv! '(first (js/Map. [[1 2] [3 4]])))))
  (is (eq "a" (jsv! '(first "abc")))))

(deftest rest-test
  (is (eq () (jsv! '(rest nil))))
  (is (eq () (jsv! '(rest []))))
  (is (eq () (jsv! '(rest #{}))))
  (is (eq () (jsv! '(rest {}))))
  (is (eq () (jsv! '(rest (js/Map. [])))))
  (is (eq #js [2 3] (jsv! '(rest [1 2 3]))))
  (is (eq #{2 3} (jsv! '(rest #{1 2 3}))))
  (is (eq #js [#js [3 4]] (jsv! '(rest (js/Map. [[1 2] [3 4]])))))
  (is (eq '("b" "c") (jsv! '(rest "abc")))))

(deftest reduce-test
  (testing "no val"
    (is (= 10 (jsv! '(reduce #(+ %1 %2) (range 5)))))
    (is (= 3 (jsv! '(reduce #(if (< %2 3)
                               (+ %1 %2)
                               (reduced %1))
                            (range 5))))
        "reduced early")
    (is (= 6 (jsv! '(reduce #(if (< %2 4)
                               (+ %1 %2)
                               (reduced %1))
                            (range 5))))
        "reduced last el")
    (is (= 0 (jsv! '(reduce #(reduced %1)
                            (range 5))))
        "reduced first el"))
  (testing "val"
    (is (= 15 (jsv! '(reduce #(+ %1 %2) 5 (range 5)))))
    (is (= 8 (jsv! '(reduce #(if (< %2 3)
                               (+ %1 %2)
                               (reduced %1))
                            5
                            (range 5))))
        "reduced early")
    (is (= 11 (jsv! '(reduce #(if (< %2 4)
                                (+ %1 %2)
                                (reduced %1))
                             5
                             (range 5))))
        "reduced last el")
    (is (= 5 (jsv! '(reduce #(reduced %1)
                            5
                            (range 5))))
        "reduced first el")
    (is (= 5 (jsv! '(reduce #(+ %2 %1) (reduced 5) (range 5))))
        "reduced val"))
  (testing "sets"
    (is (= 10 (jsv! '(reduce #(+ %1 %2) #{1 2 3 4})))))
  (testing "maps"
    (is (= 10 (jsv! '(reduce #(+ %1 (second %2))
                             0
                             (js/Map. [[:a 1] [:b 2] [:c 3] [:d 4]]))))))
  (testing "objects"
    (is (= 10 (jsv! '(reduce #(+ %1 (second %2))
                             0
                             (js/Object.entries {:a 1 :b 2 :c 3 :d 4})))))
    (is (= 10 (jsv! '(reduce #(+ %1 %2)
                             0
                             (js/Object.values {:a 1 :b 2 :c 3 :d 4})))))
    (is (= 10 (jsv! '(reduce #(+ %1 (second %2))
                             0
                             {:a 1 :b 2 :c 3 :d 4}))))))


(deftest reduced-test
  (is (jsv! '(reduced? (reduced 5))))
  (is (= 4 (jsv! '(deref (reduced 4))))))

(deftest seq-test
  (is (= "abc" (jsv! '(seq "abc"))))
  (is (eq '(1 2 3) (jsv! '(seq [1 2 3]))))
  (is (eq '([:a 1] [:b 2]) (jsv! '(seq {:a 1 :b 2}))))
  (is (eq (js/Set. [1 2 3])
          (jsv! '(seq #{1 2 3}))))
  (is (eq (js/Map. #js[#js[1 2] #js[3 4]])
          (jsv! '(seq (js/Map. [[1 2] [3 4]])))))
  (testing "empty"
    (is (= nil (jsv! '(seq nil))))
    (is (= nil (jsv! '(seq []))))
    (is (= nil (jsv! '(seq {}))))
    (is (= nil (jsv! '(seq #{}))))
    (is (= nil (jsv! '(seq (js/Map.))))))
  (is (eq #js [0 2 4 6 8]
          (jsv! '(loop [evens []
                        nums (range 10)]
                   (if-some [x (first nums)]
                     (recur (if (case x
                                  (0 2 4 6 8 10) true
                                  false)
                              (conj evens x)
                              evens)
                            (rest nums))
                     evens))))))

(defn init []
  (cljs.test/run-tests 'clava.compiler-test))
