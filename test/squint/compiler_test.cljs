(ns squint.compiler-test
  (:require
   [clojure.string :as str]
   [clojure.test :as t :refer [async deftest is testing]]
   [squint.compiler :as compiler]
   [squint.jsx-test]
   [squint.string-test]
   [squint.test-utils :refer [eq js! jss! jsv!]]))

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
                   (vec (map name [1 2 3]))))]
    (is (eq #js [1 1 1]
            (js/eval s))))
  (let [s (jss! '(let [name (fn [_] 1)
                       name (fn [_] 2)]
                   (vec (map name [1 2 3]))))]
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
                     (foo x)))))
  (is (eq [:bar 1 2]
          (jsv! '(do (defprotocol IFoo (foo [_]) (bar [_] [_ a b]))
                     (deftype Foo []
                       IFoo
                       (foo [_] :foo)
                       (bar [_] [:bar])
                       (bar [_ a b] [:bar a b]))
                     (bar (->Foo) 1 2))))))

(deftest satisfies?-test
  #_#_(is (jsv! '(do (defprotocol IFoo)
                     (satisfies? (reify IFoo)))))
  (is (jsv! '(do (defprotocol IFoo (-foo [_]))
                 (satisfies? (reify IFoo
                               (-foo [_] "bar"))))))
  (is (jsv! '(do (defprotocol IFoo)
                 (deftype Foo [] IFoo)
                 (satisfies? IFoo (->Foo)))))
  (is (jsv! '(do (defprotocol IFoo (-foo [_]))
                 (deftype Foo [] IFoo (-foo [_] "bar"))
                 (satisfies? IFoo (->Foo)))))
  (is (jsv! '(do (defprotocol IFoo)
                 (extend-type number IFoo)
                 (satisfies? IFoo 1))))
  (is (jsv! '(do (defprotocol IFoo)
                 (extend-type string IFoo)
                 (satisfies? IFoo "bar")))))

(deftest set-test
  (is (eq (js/Set. #js [1 2 3]) (jsv! #{1 2 3})))
  (is (eq (js/Set. [1 2 3]) (jsv! '(set [1 2 3]))))
  (is (eq (js/Set. [#js ["a" 1] #js ["b" 2]])
          (jsv! '(set {:a 1 :b 2}))))
  (is (eq (js/Set. [#js ["a" 1] #js ["b" 2]])
          (jsv! '(set (js/Map. [[:a 1] [:b 2]])))))
  (is (eq (js/Set.) (jsv! '(set))))
  (is (eq (js/Set.) (jsv! '(set nil)))))

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
  (is (= "bar" (jsv! '(:foo {:foo :bar}))))
  (is (= "bar" (jsv! '(:foo nil :bar))))
  (is (= "bar" (jsv! '(let [{:keys [foo] :or {foo :bar}} nil]
                        foo)))))

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

(deftest comp-test
  (is (eq "0" (jsv! '((comp) "0")))
      "0-arity is identity")
  (is (eq 4 (jsv! '((comp inc) 3)))
      "1-arity")
  (is (eq "0123" (jsv! '((comp #(str % "3") #(str % "2") #(str % "1"))
                         "0")))
      "order"))

(deftest conj-test
  (testing "corner cases"
    (is (eq [], (jsv! '(conj))))
    (is (= true, (jsv! '(vector? (conj)))))
    (is (eq '(), (jsv! '(conj nil))))
    (is (= true, (jsv! '(array? (conj nil)))))
    (is (eq [1 2] (jsv! '(conj nil 1 2)))))
  (testing "arrays"
    (is (eq [1 2 3 4] (jsv! '(conj [1 2 3 4]))))
    (is (eq [1 2 3 4] (jsv! '(conj [1 2 3] 4))))
    (is (eq [1 2 3 4] (jsv! '(conj [1 2] 3 4)))))
  (testing "lists"
    (is (eq '(1 2 3 4) (jsv! '(conj '(1 2 3 4)))))
    (is (eq '(1 2 3 4) (jsv! '(conj '(2 3 4) 1))))
    (is (eq '(1 2 3 4) (jsv! '(conj '(3 4) 2 1)))))
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
  (testing "lazy iterable"
    (is (eq [0 1 2 3 4 5]
            (vec (jsv! '(conj (map inc [4])
                              0 1 2 3 4))))))
  (testing "other types"
    (is (thrown? js/Error (jsv! '(conj "foo"))))))

(deftest conj!-test
  (testing "corner cases"
    (is (eq [], (jsv! '(conj!))))
    (is (eq '(), (jsv! '(conj! nil))))
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
  (testing "lists"
    (is (eq '(1 2 3 4) (jsv! '(conj '(1 2 3 4)))))
    (is (eq '(1 2 3 4) (jsv! '(conj '(2 3 4) 1))))
    (is (eq '(1 2 3 4) (jsv! '(let [x '(2 3 4)]
                                (conj! x 1)
                                x))))
    (is (eq '(1 2 3 4) (jsv! '(conj! '(3 4) 2 1))))
    (is (eq '(1 2 3 4) (jsv! '(let [x '(3 4)]
                                (conj! x 2 1)
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

(deftest contains?-test
  (testing "corner cases"
    (is (= false (jsv! '(contains? nil nil))))
    (is (= false (jsv! '(contains? 1 nil))))
    (is (= false (jsv! '(contains? 1 1))))
    (is (= false (jsv! '(contains? "foo" "foo"))))
    (is (= false (jsv! '(contains? "foo" nil)))))
  (testing "arrays"
    (is (= true (jsv! '(contains? [1 2 3] 0))))
    (is (= true (jsv! '(contains? [1 2 3] 1))))
    (is (= true (jsv! '(contains? [1 2 3] 2))))
    (is (= false (jsv! '(contains? [1 2 3] 3)))))
  (testing "sets"
    (is (= false (jsv! '(contains? #{1 2 3} 0))))
    (is (= true (jsv! '(contains? #{1 2 3} 1))))
    (is (= true (jsv! '(contains? #{1 2 3} 2))))
    (is (= true (jsv! '(contains? #{1 2 3} 3))))
    (is (= true (jsv! '(contains? #{1 2 3 nil} nil)))))
  (testing "objects"
    (is (= true (jsv! '(contains? {:a 1} :a))))
    (is (= false (jsv! '(contains? {:a 1} :b)))))
  (testing "maps"
    (is (= true (jsv! '(contains? (js/Map. [[:a 1]]) :a))))
    (is (= false (jsv! '(contains? (js/Map. [[:a 1]]) :b))))))

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
  (testing "corner cases"
    (is (= nil (jsv! '(get nil nil))))
    (is (= nil (jsv! '(get nil 0))))
    (is (= nil (jsv! '(get 1 nil))))
    (is (= nil (jsv! '(get "1" nil))))
    (is (= nil (jsv! '(get true nil))))
    (is (= nil (jsv! '(get :foo nil)))))
  (testing "maps"
    (is (eq nil (jsv! '(get (js/Map. [["my-key" 1]]) nil))))
    (is (eq 1 (jsv! '(get (js/Map. [["my-key" 1]]) "my-key"))))
    (is (identical? js/undefined (jsv! '(get (js/Map. [["my-key" 1]]) "bad-key"))))
    (is (eq 3 (jsv! '(get (js/Map. [["my-key" 1]]) "bad-key" 3))))
    (is (identical? nil (jsv! '(get (js/Map. [[:my-key nil]]) :my-key)))))
  (testing "arrays"
    (is (eq nil (jsv! '(get ["val1" "val2" "val3"] nil))))
    (is (eq "val2" (jsv! '(get ["val1" "val2" "val3"] 1))))
    (is (identical? js/undefined (jsv! '(get ["val1" "val2" "val3"] 10))))
    (is (eq "val2" (jsv! '(get ["val1" "val2" "val3"] 10 "val2"))))
    (is (identical? nil (jsv! '(get [nil] 0)))))
  (testing "sets"
    (is (eq nil (jsv! '(get #{1 2 3} 0))))
    (is (eq nil (jsv! '(get #{1 2 3 nil} nil))))
    (is (eq 1 (jsv! '(get #{1 2 3} 1))))
    (is (eq 2 (jsv! '(get #{1 2 3} 2))))
    (is (eq 3 (jsv! '(get #{1 2 3} 3)))))
  (testing "objects"
    (is (eq nil (jsv! '(get {"my-key" 1} nil))))
    (is (eq 1 (jsv! '(get {"my-key" 1} "my-key"))))
    (is (identical? js/undefined (jsv! '(get {"my-key" 1} "bad-key"))))
    (is (eq 3 (jsv! '(get {"my-key" 1} "bad-key" 3))))
    (is (identical? nil (jsv! '(get {"my-key" nil} "my-key"))))))

(deftest first-test
  (is (= nil (jsv! '(first nil))))
  (is (= nil (jsv! '(first []))))
  (is (= nil (jsv! '(first #{}))))
  (is (= nil (jsv! '(first {}))))
  (is (= nil (jsv! '(first (js/Map. [])))))
  (is (= 1 (jsv! '(first [1 2 3]))))
  (is (= 1 (jsv! '(first #{1 2 3}))))
  (is (eq #js [1 2] (jsv! '(first (js/Map. [[1 2] [3 4]])))))
  (is (eq "a" (jsv! '(first "abc"))))
  ;; keywords are translated to strings
  (is (eq "a" (jsv! '(first :abd)))))

(deftest ffirst-test
  (is (= "f" (jsv! '(ffirst ["foo"]))))
  (is (= "f" (jsv! '(ffirst "foo")))))

(deftest rest-test
  (is (eq () (jsv! '(vec (rest nil)))))
  (is (eq () (jsv! '(vec (rest [])))))
  (is (eq () (jsv! '(vec (rest #{})))))
  (is (eq () (jsv! '(vec (rest {})))))
  (is (eq () (jsv! '(vec (rest (js/Map. []))))))
  (is (eq #js [2 3] (jsv! '(vec (rest [1 2 3])))))
  (is (eq #{2 3} (jsv! '(vec (rest #{1 2 3})))))
  (is (eq #js [#js [3 4]] (jsv! '(vec (rest (js/Map. [[1 2] [3 4]]))))))
  (is (eq '("b" "c") (jsv! '(vec (rest "abc")))))
  (is (= 1 (jsv! '(first (rest (range))))) "infinite rest"))


(deftest last-test
  (is (= nil (jsv! '(last nil))))
  (is (= nil (jsv! '(last []))))
  (is (= nil (jsv! '(last {}))))
  (is (= nil (jsv! '(last #{}))))
  (is (= nil (jsv! '(last (js/Map.)))))
  (is (= nil (jsv! '(last (map inc nil)))) "lazy iterable")
  (is (= 4 (jsv! '(last [1 2 3 4]))))
  (is (eq ["d" 4] (jsv! '(last {:a 1 :b 2 :c 3 :d 4}))))
  (is (#{1 2 3 4} (jsv! '(last #{1 2 3 4}))))
  (is (eq ["d" 4] (jsv! '(last (js/Map. [[:a 1] [:b 2] [:c 3] [:d 4]])))))
  (is (= 4 (jsv! '(last (range 5))))))

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

(deftest cons-test
  (is (eq [0] (jsv! '(vec (cons 0 nil)))))
  (is (eq [0 1 2 3 4] (jsv! '(vec (cons 0 [1 2 3 4])))))
  (is (eq [0 1 2 3 4] (jsv! '(vec (cons 0 (map inc (range 4)))))))
  (is (.has (jsv! '(into #{} (cons 0 #{1 2 3 4}))) 0))
  (is (eq [0 [:a 1] [:b 2]] (jsv! '(vec (cons 0 {:a 1 :b 2})))))
  (is (eq [0 [:a 1] [:b 2]] (jsv! '(vec (cons 0 (js/Map. [[:a 1] [:b 2]])))))))

(deftest map-test
  (is (eq [1 2 3 4 5] (jsv! '(vec (map inc [0 1 2 3 4])))))
  (is (every? (set (jsv! '(map inc #{0 1 2 3 4})))
              [1 2 3 4 5]))
  (is (eq [[:a 1] [:b 2]]
          (jsv! '(vec (map #(vector (first %) (inc (second %)))
                           {:a 0 :b 1})))))
  (is (eq ["A" "B" "C"]
          (jsv! '(vec (map #(.toUpperCase %) "abc")))))
  (is (eq [[0 1] [1 2] [2 3] [3 4] [4 5]]
          (jsv! '(vec (map #(vector (first %) (inc (second %)))
                           (-> [[0 0] [1 1] [2 2] [3 3] [4 4]]
                               (js/Map.)))))))
  (testing "nil"
    (is (eq () (jsv! '(vec (map inc nil)))))
    (is (eq () (jsv! '(vec (map inc js/undefined))))))
  (testing "multiple colls"
    (is (eq [4 6] (jsv! '(vec (map + [1 2] [3 4])))))
    (testing "reusing seq"
      (is (eq [2 2] (jsv! '(let [sum (map + [1 2] [3 4])]
                             [(count sum) (count sum)])))))
    (is (eq [4 6] (jsv! '(vec (map + [1 2] [3 4])))))
    (is (eq ["1y" "2o"] (jsv! '(vec (map str [1 2] "yolo")))))
    (is (eq [[1 4 7] [2 5 8] [3 6 9]]
            (jsv! '(vec (apply map vector [[1 2 3] [4 5 6] [7 8 9]])))))
    (is (eq [[1,4],[2,5],[3,6]]
            (jsv! '(vec (map vector [1 2 3] [4 5 6 7 8 9])))))
    (is (eq []
            (jsv! '(vec (map vector nil nil nil)))))))

(deftest mapv-test
  (is (= true (jsv! '(vector? (mapv inc [0 1 2 3 4])))))
  (is (eq ["A" "B" "C"]
          (jsv! '(mapv #(.toUpperCase %) "abc"))))
  (testing "nil"
    (is (eq [] (jsv! '(mapv inc nil)))))
  (testing "multiple colls"
    (is (eq [4 6] (jsv! '(mapv + [1 2] [3 4]))))))

(deftest filter-test
  (is (eq [2 4 6 8] (jsv! '(vec (filter even? [1 2 3 4 5 6 7 8 9])))))
  (is (every? (set (jsv! '(vec (filter even? #{1 2 3 4 5 6 7 8 9}))))
              [2 4 6 8]))
  (is (eq [[:a 1]] (jsv! '(vec (filter #(= :a (first %)) {:a 1 :b 2})))))
  (testing "nil"
    (is (eq () (jsv! '(vec (filter even? nil)))))
    (is (eq () (jsv! '(vec (filter even? js/undefined)))))))

(deftest filterv-test
  (is (= true (jsv! '(vector? (filterv even? [1 2 3 4 5 6 7 8 9])))))
  (is (eq [[:a 1]] (jsv! '(filterv #(= :a (first %)) {:a 1 :b 2})))))

(deftest remove-test
  (is (eq [2 4 6 8] (jsv! '(vec (remove odd? [1 2 3 4 5 6 7 8 9])))))
  (is (every? (set (jsv! '(vec (remove odd? #{1 2 3 4 5 6 7 8 9}))))
              [2 4 6 8]))
  (is (eq [[:a 1]] (jsv! '(vec (remove #(not= :a (first %)) {:a 1 :b 2})))))
  (testing "nil"
    (is (eq () (jsv! '(vec (remove odd? nil)))))
    (is (eq () (jsv! '(vec (remove odd? js/undefined)))))))

(deftest map-indexed-test
  (is (eq [[0 0] [1 1] [2 2] [3 3] [4 4]]
          (jsv! '(map-indexed vector [0 1 2 3 4]))))
  (is (= 20 (apply + (jsv! '(map-indexed + #{0 1 2 3 4})))))
  (is (eq [[0 :a 1] [1 :b 2]]
          (jsv! '(map-indexed #(vector %1 (first %2) (inc (second %2)))
                              {:a 0 :b 1}))))
  (is (eq [[0 "A"] [1 "B"] [2 "C"]]
          (jsv! '(map-indexed #(vector %1 (.toUpperCase %2))
                              "abc"))))
  (is (eq [[0 0 1] [1 1 2] [2 2 3] [3 3 4] [4 4 5]]
          (jsv! '(map-indexed
                  #(vector %1 (first %2) (inc (second %2)))
                  (-> [[0 0] [1 1] [2 2] [3 3] [4 4]]
                      (js/Map.))))))
  (testing "nil"
    (is (eq () (jsv! '(map-indexed vector nil))))
    (is (eq () (jsv! '(map-indexed vector js/undefined))))))

(deftest complement-test
  (is (= false (jsv! '((complement (constantly true))))))
  (is (= true (jsv! '((complement (constantly false))))))
  (is (= true (jsv! '((complement (constantly false)) "with some" "args" 1 :a))))
  (is (= true (jsv! '(let [not-contains? (complement contains?)]
                       (not-contains? [2 3 4] 5)))))
  (is (= false (jsv! '(let [not-contains? (complement contains?)]
                        (not-contains? [2 3 4] 2)))))
  (is (= true (jsv! '(let [first-elem-not-1? (complement (fn [x] (= 1 (first x))))]
                       (first-elem-not-1? [2 3])))))
  (is (= false (jsv! '(let [first-elem-not-1? (complement (fn [x] (= 1 (first x))))]
                        (first-elem-not-1? [1 2]))))))

(deftest constantly-test
  (is (= "abc" (jsv! '((constantly "abc")))))
  (is (= 10 (jsv! '((constantly 10)))))
  (is (= true (jsv! '((constantly true)))))
  (is (= nil (jsv! '((constantly nil)))))
  (is (= nil (jsv! '((constantly nil) "with some" "args" 1 :a)))))

(deftest list?-test
  (is (= true (jsv! '(list? '(1 2 3 4)))))
  (is (= true (jsv! '(list? (list 1 2 3)))))
  (is (= false (jsv! '(list? nil))))
  (is (= false (jsv! '(list? [1 2 3]))))
  (is (= false (jsv! '(list? {:a :b}))))
  (is (= false (jsv! '(list? #{:a :b})))))

(deftest vector?-test
  (is (= false (jsv! '(vector? '(1 2 3 4)))))
  (is (= false (jsv! '(vector? nil))))
  (is (= true (jsv! '(vector? [1 2 3]))))
  (is (= false (jsv! '(vector? {:a :b}))))
  (is (= false (jsv! '(vector? #{:a :b})))))

(deftest vec-test
  (is (eq [] (jsv! '(vec))))
  (is (eq [] (jsv! '(vec []))))
  (is (eq #js [0 1 2 3] (jsv! '(vec [0 1 2 3]))))
  (is (eq #js [0 1 2 3] (jsv! '(vec (range 4)))))
  (is (eq #{0 1 2 3} (jsv! '(vec #{0 1 2 3}))))
  (is (eq [["one" 1] ["two" 2]] (jsv! '(vec {:one 1 :two 2})))))

(deftest list-test
  (testing "creates a list of elements"
    (is (eq '(1 2 3) (jsv! '(list 1 2 3)))))
  (testing "accepts a single, numeric element in the list"
    (is (eq '(23) (jsv! '(list 23)))))
  (testing "creates an empty list"
    (is (eq '() (jsv! '(list))))))

(deftest instance-test
  (is (true? (jsv! '(instance? js/Array []))))
  (is (false? (jsv! '(instance? js/String [])))))

(deftest concat-test
  (is (eq [] (jsv! '(vec (concat nil)))))
  (is (eq [1] (jsv! '(vec (concat nil [] [1])))))
  (is (eq [0 1 2 3 4 5 6 7 8 9] (jsv! '(vec (concat [0 1 2 3] [4 5 6] [7 8 9])))))
  (is (eq [["a" "b"] ["c" "d"] 2] (jsv! '(vec (concat {"a" "b" "c" "d"} [2]))))))

(deftest mapcat-test
  (is (eq [] (jsv! '(vec (mapcat identity nil)))))
  (is (eq [0 1 2 3 4 5 6 7 8 9] (jsv! '(vec (mapcat identity [[0 1 2 3] [4 5 6] [7 8 9]])))))
  (is (eq ["a" "b" "c" "d"] (jsv! '(vec (mapcat identity {"a" "b" "c" "d"})))))
  (testing "multiple colls"
    (is (eq ["a" 1 "b" 2] (jsv! '(vec (mapcat list [:a :b :c] [1 2])))))))

(deftest interleave-test
  (is (eq [] (jsv! '(vec (interleave nil nil)))))
  (is (eq [] (jsv! '(vec (interleave [1 2] nil)))))
  (is (eq [] (jsv! '(vec (interleave [1 2 3] nil)))))
  (is (eq [] (jsv! '(vec (interleave [1 2 3] ["a" "b"] nil)))))
  (is (eq [1 "a" 2 "b"] (jsv! '(vec (interleave [1 2 3] ["a" "b"])))))
  (is (eq [1 "a" 2 "b" 3 "c"] (jsv! '(vec (interleave [1 2 3] ["a" "b" "c"])))))
  (is (eq [1 "a" 2 "b"] (jsv! '(vec (interleave [1 2] ["a" "b" "c"]))))))

(deftest interpose-test
  (is (eq [] (jsv! '(vec (interpose "," nil)))))
  (is (eq [1 ",", 2] (jsv! '(vec (interpose ",", [1 2])))))
  (is (eq [0 nil 1 nil 2] (jsv! '(vec (take 5 (interpose nil (range))))))))

(deftest select-keys-test
  (is (eq {:a 1 :b 2} (jsv! '(select-keys {:a 1 :b 2 :c 3} [:a :b]))))
  (let [m (jsv! '(select-keys (js/Map. [[:a 1] [:b 2] [:c 3]]) [:a :b]))]
    (is (instance? js/Map m))
    (is (= 1 (.get m "a")))
    (is (= 2 (.get m "b")))
    (is (not (.has m "c")))))

(deftest partition-test
  (is (eq [[0 1 2 3] [4 5 6 7] [8 9 10 11] [12 13 14 15] [16 17 18 19]] (jsv! '(vec (partition 4 (range 20))))))
  (is (eq [[0 1 2 3] [4 5 6 7] [8 9 10 11] [12 13 14 15] [16 17 18 19]] (jsv! '(vec (partition 4 (range 22))))))
  (testing "step"
    (is (eq [[0 1 2 3] [6 7 8 9] [12 13 14 15]] (jsv! '(vec (partition 4 6 (range 20)))))))
  (testing "step < n"
    (is (eq [[0 1 2 3] [3 4 5 6] [6 7 8 9] [9 10 11 12] [12 13 14 15] [15 16 17 18]] (jsv! '(vec (partition 4 3 (range 20)))))))
  (testing "pad"
    (is (eq [[0 1 2] [6 7 8] [12 13 14] [18 19 "a"]] (jsv! '(vec (partition 3 6 ["a"] (range 20))))))
    (is (eq [[0 1 2 3] [6 7 8 9] [12 13 14 15] [18 19 "a"]] (jsv! '(vec (partition 4 6 ["a"] (range 20))))))
    (is (eq [[0 1 2 3] [6 7 8 9] [12 13 14 15] [18 19 "a" "b"]] (jsv! '(vec (partition 4 6 ["a" "b" "c" "d"] (range 20)))))))
  (testing "infinite seq"
    (is (eq [[0 1 2 3] [6 7 8 9] [12 13 14 15]] (jsv! '(vec (take 3 (partition 4 6 (range)))))))
    (is (eq [[0 1 2 3] [2 3 4 5] [4 5 6 7]] (jsv! '(vec (take 3 (partition 4 2 (range)))))))))
         

(deftest partition-all-test
  (is (eq [[0 1 2 3] [4 5 6 7] [8 9 10 11] [12 13 14 15] [16 17 18 19]] (jsv! '(vec (partition-all 4 (range 20))))))
  (is (eq [[0 1 2 3] [4 5 6 7] [8 9 10 11] [12 13 14 15] [16 17 18 19] [20 21]] (jsv! '(vec (partition-all 4 (range 22))))))
  (testing "step"
    (is (eq [[0 1 2 3] [6 7 8 9] [12 13 14 15] [18 19]] (jsv! '(vec (partition-all 4 6 (range 20)))))))
  (testing "step < n"
    (is (eq [[0 1 2 3] [3 4 5 6] [6 7 8 9] [9 10 11 12] [12 13 14 15] [15 16 17 18] [18 19]] (jsv! '(vec (partition-all 4 3 (range 20)))))))
  (testing "infinite seq"
    (is (eq [[0 1 2 3] [6 7 8 9] [12 13 14 15]] (jsv! '(vec (take 3 (partition-all 4 6 (range)))))))
    (is (eq [[0 1 2 3] [2 3 4 5] [4 5 6 7]] (jsv! '(vec (take 3 (partition-all 4 2 (range)))))))))

(deftest merge-test
  (testing "corner cases"
    (is (eq [1] (jsv! '(merge [] 1))))
    (is (eq (js/Set. #js [1]) (jsv! '(merge #{} 1))))
    (is (eq '(1) (jsv! '(merge (list) 1))))
    (is (thrown? js/Error (jsv! '(merge 1 1)))))
  (is (eq {:a 1} (jsv! '(merge nil {:a 1}))))
  (is (eq {:a 1} (jsv! '(merge {:a 2} {:a 1}))))
  (is (eq {:a 1 :b 2} (jsv! '(merge {:a 1} {:b 2}))))
  (let [s (jsv! '(merge (js/Map.) {:a 1 :b 2}))]
    (is (instance? js/Map s))
    (doseq [k ["a" "b"]]
      (is (.has s k) (str "key: " k))))
  (is (true? (jsv! '(let [obj1 {:a 2}
                          obj2 (merge obj1 {:a 1})]
                      (not (identical? obj1 obj2)))))))

(deftest into-test
  (testing "corner cases"
    (is (eq [], (jsv! '(into))))
    (is (= true, (jsv! '(vector? (into nil [1])))))
    (is (eq nil, (jsv! '(into nil))))      ; same as clojure, but clojureScript throws error for arity 1
    (is (eq [1 2] (jsv! '(into nil [1 2])))))
  (testing "arrays"
    (is (eq [1 2 3 4] (jsv! '(into [1 2 3 4]))))
    (is (eq [1 2 3 4] (jsv! '(into [1 2 3] [4]))))
    (is (eq [1 2 3 4] (jsv! '(into [1 2] [3 4])))))
  (testing "lists"
    (is (eq '(1 2 3 4) (jsv! '(into '(1 2 3 4)))))
    (is (eq '(1 2 3 4) (jsv! '(into '(2 3 4) [1]))))
    (is (eq '(1 2 3 4) (jsv! '(into '(3 4) '(2 1))))))
  (testing "sets"
    (is (eq (js/Set. #js [1 2 3 4]) (jsv! '(into #{1 2 3 4}))))
    (is (eq (js/Set. #js [1 2 3 4]) (jsv! '(into #{1 2 3} [4]))))
    (is (eq (js/Set. #js [1 2 3 4]) (jsv! '(into #{1 2} [3 4])))))
  (testing "objects"
    (is (eq #js {:a "b" :c "d"} (jsv! '(into {:a "b" :c "d"}))))
    (is (eq #js {"1" 2 "3" 4} (jsv! '(into {"1" 2} [["3" 4]]))))
    (is (eq #js {"1" 2 "3" 4 "5" 6} (jsv! '(into {"1" 2} '(["3" 4] ["5" 6]))))))
  (testing "maps"
    (is (eq (js/Map. #js [#js ["a" "b"] #js ["c" "d"]])
            (jsv! '(into (js/Map. [["a" "b"] ["c" "d"]])))))
    (is (eq (js/Map. #js [#js [1 2] #js [3 4]])
            (jsv! '(into (js/Map. [[1 2]]) [[3 4]]))))
    (is (eq (js/Map. #js [#js [1 2] #js [3 4] #js [5 6]])
            (jsv! '(into (js/Map. [[1 2]]) [[3 4] [5 6]])))))
  (testing "other types"
    (is (thrown? js/Error (jsv! '(into "foo" []))))))


(deftest iterable-protocol
  (is (eq [true true [1 2 3 4 5]]
          (jsv! '(do (deftype Foo []
                       IIterable
                       (-iterator [_]
                         ;; -iterator must return a JS Iterator object,
                         ;; not another iterable
                         (-iterator [0 1 2 3 4])))
                     (let [foo (->Foo)]
                       [(seqable? foo)
                        (= foo (seq foo))
                        (vec (map inc (->Foo)))]))))))

(deftest repeat-test
  (is (eq [] (jsv! '(vec (interleave (repeat 1) [])))))
  (is (eq [1 "a" 1 "b"] (jsv! '(vec (interleave (repeat 1) ["a" "b"])))))
  (is (eq [1 "a" 1 "b" 1 "c"] (jsv! '(vec (interleave (repeat 1) ["a" "b" "c"])))))
  (is (eq [1 "a" 1 "b"] (jsv! '(vec (interleave (repeat 2 1) ["a" "b" "c"])))))
  (testing "satisfies IIterable"
    (is (= true (jsv! '(satisfies? IIterable (repeat 1))))))
  (testing "invalid arity"
    (is (thrown? js/Error (jsv! '(repeat))))))

(deftest take-test
  (is (eq [] (jsv! '(vec (take 1 nil)))))
  (is (eq [] (jsv! '(vec (take 0 (repeat 1))))))
  (is (eq [1 1 1] (jsv! '(vec (take 3 (repeat 1))))))
  (is (eq ["a" "b"] (jsv! '(vec (take 2 ["a" "b" "c"])))))
  (is (eq ["a" "b" "c"] (jsv! '(vec (take 5 ["a" "b" "c"])))))
  (is (eq [["a" 1] ["b" 2]] (jsv! '(vec (take 2 {"a" 1 "b" 2 "c" 3})))))
  (is (= 2
         (jsv! '(let [o {:count 0}
                      s (range)
                      s' (map (fn [x]
                                (assoc! o :count (inc (:count o)))
                                x)
                              s)
                      s'' (take 2 s')]
                  (vec s'')
                  (:count o))))))

(deftest take-while-test
  (is (eq [2 4] (jsv! '(vec (take-while even? [2 4 5])))))
  (let [taken (jsv! '(take-while even? [1 1 1 2 3 4]))]
    (is (eq (take-while even? [1 1 1 2 3 4]) (vec taken)))
    ;; iterating over dropped second time, still works:
    (is (eq (take-while even? [1 1 1 2 3 4]) (vec taken))))
  (is (eq (take-while odd? nil) (vec (jsv! '(take-while odd? nil))))))

(deftest take-nth-test
  (is ["a" "a" "a"] (jsv! '(vec (take 3 (take-nth 0 ["a" "b"])))))
  (is [1 4 7] (jsv! '(vec (take-nth 3 [1 2 3 4 5 6 7 8 9])))))

(deftest +-test
  (is (zero? (jsv! '(apply + []))))
  (is (= 1 (jsv! '(apply + [1]))))
  (is (= 6 (jsv! '(apply + [1 2 3]))))
  (is (= 6 (jsv! '(apply + (range 4))))))

(deftest partial-test
  (let [f (jsv! '(partial + 2))]
    (is (= 2 (f)))
    (is (= 3 (f 1)))
    (is (= 5 (f 1 1 1)))))

(deftest cycle-test
  (is (eq (take 10 (cycle [1 2 3])) (vec (jsv! '(take 10 (cycle [1 2 3])))))))

(deftest nth-test
  (is (nil? (jsv! '(nth nil 1))))
  (is (eq :default (jsv! '(nth nil 1 :default))))
  (is (= 1 (jsv! '(nth [1 2 3] 0))))
  (is (eq :default (jsv! '(nth [1 2 3] 5 :default)))))

(deftest drop-test
  (let [dropped (jsv! '(drop 3 (range 6)))]
    (is (eq [3 4 5] (vec dropped)))
    ;; iterating over dropped second time, still works:
    (is (eq [3 4 5] (vec dropped))))
  (is (eq (drop 2 nil) (vec (jsv! '(drop 2 nil))))))

(deftest drop-while-test
  (let [dropped (jsv! '(drop-while odd? [1 1 1 2 3 4]))]
    (is (eq (drop-while odd? [1 1 1 2 3 4]) (vec dropped)))
    ;; iterating over dropped second time, still works:
    (is (eq (drop-while odd? [1 1 1 2 3 4]) (vec dropped))))
  (is (eq (drop-while odd? nil) (vec (jsv! '(drop-while odd? nil))))))

(deftest distinct-test
  (doseq [coll [[1 1 1 2 3 4 1] nil]]
    (is (eq (distinct coll) (vec (jsv! `(distinct ~coll)))))))

(deftest update-test
  (is (eq {:a 2} (jsv! '(update {:a 1} :a inc))))
  (is (eq {:a 3} (jsv! '(update {:a 1} :a + 2)))))

(deftest update-in-test
  (is (eq {:a {:b 2}} (jsv! '(update-in {:a {:b 1}} [:a :b] inc))))
  (is (eq {:a {:b 4}} (jsv! '(update-in {:a {:b 2}} [:a :b] + 2))))
  (is (eq (update-in {:a {:b {}}} [:a :b :c] (fnil inc 0))
          (jsv! (update-in {:a {:b {}}} [:a :b :c] (fnil inc 0))))))

(deftest every?-test
  (is (= true (jsv! '(every? odd? nil))))
  (is (= true (jsv! '(every? odd? []))))
  (is (= true (jsv! '(every? odd? [1 3 5]))))
  (is (= false (jsv! '(every? odd? [1 3 6]))))
  (is (= true (jsv! '(every? str [1 3 6]))))
  (is (= true (jsv! '(every? str [0 1 3 6]))))
  (is (= false (jsv! '(every? identity [0 1 3 6])))))

(deftest not-every?-test
  (is (= false (jsv! '(not-every? odd? []))))
  (is (= false (jsv! '(not-every? odd? [1 3 5])))))

(deftest keep-test
  (is (eq (keep #(when (odd? %) (inc %)) [1 2 3])
          (vec (jsv! '(keep #(when (odd? %) (inc %)) [1 2 3]))))))

(deftest reverse-test
  (is (eq (reverse [1 2 3]) (jsv! '(reverse [1 2 3]))))
  (is (eq (reverse (range 10)) (jsv! '(reverse (range 10))))))

(deftest sort-test
  (is (eq (sort [4 2 3 1]) (jsv! '(sort [4 2 3 1]))))
  (is (eq (sort - [4 2 3 1]) (jsv! '(sort - [4 2 3 1]))))
  ;; TODO: implement compare?
  )

(deftest shuffle-test
  (let [shuffled (jsv! '(shuffle [1 2 3 4]))]
    (doseq [i shuffled]
      (is (contains? #{1 2 3 4} i)))))

(deftest some-test
  (is (= 1 (jsv! '(some #(when (odd? %) %) [2 1 2 1])))))

(deftest not-any?-test
  (is (= true (jsv! '(not-any? odd? [2 2 6 4]))))
  (is (= true (jsv! '(not-any? odd? [])))))

(deftest replacement-test
  (is (eq (replace {:a :b} [:a :a :c :c :a :c])
          (jsv! '(replace {:a :b} [:a :a :c :c :a :c]))))
  (is (instance? js/Array (jsv! '(replace {:a :b} [:a :a :c :c :a :c]))))
  (is (eq [:b :b] (vec (jsv! '(take 2 (replace {:a :b} (repeat :a))))))))

(deftest empty?-test
  (is (jsv! '(empty? [])))
  (is (not (jsv! '(empty? [1]))))
  (is (jsv! '(empty? {})))
  (is (not (jsv! '(empty? {:a 1}))))
  (is (jsv! '(empty? #{})))
  (is (not (jsv! '(empty? #{1})))))

(deftest repeatedly-test
  (is (every? #(< % 10)
              (take 10 (jsv! '(repeatedly #(rand-int 10))))))
  (is (every? #(< % 10)
              (jsv! '(repeatedly 5 #(rand-int 10))))))

(deftest group-by-test
  (is (eq [1 3] (jsv! '(get (group-by odd? [1 2 3 4]) true))))
  (is (eq [2 4] (jsv! '(get (group-by odd? [1 2 3 4]) false))))
  (is (eq [[1 [1 2 3]]] (jsv! '(get (group-by second [[1 [1 2 3]] [2 [3 4 5]]]) [1 2 3])))))

(deftest frequencies-test
  (is (eq 3 (jsv! '(get (frequencies [:a :a :b :b :b]) :b)))))

(deftest butlast-test
  (is (eq nil (jsv! '(butlast nil))))
  (is (eq nil (jsv! '(butlast []))))
  (is (eq nil (jsv! '(butlast [1]))))
  (is (eq [1] (jsv! '(butlast [1 2])))))

(deftest drop-last-test
  (is (eq [] (jsv! '(vec (drop-last nil)))))
  (is (eq [] (jsv! '(vec (drop-last [])))))
  (is (eq [] (jsv! '(vec (drop-last [1])))))
  (is (eq [1] (jsv! '(vec (drop-last 2 [1 2 3]))))))

(deftest split-at-test
  (is (eq [[1] [2 3]] (jsv! '(mapv vec (split-at 1 [1 2 3])))))
  (is (eq [[] []] (jsv! '(mapv vec (split-at 1 nil)))))
  (is (eq [[] [1 2 3]] (jsv! '(mapv vec (split-at 0 [1 2 3]))))))

(deftest split-with-test
  (is (eq [[1] [2 3]] (jsv! '(mapv vec (split-with odd? [1 2 3])))))
  (is (eq [[] []] (jsv! '(mapv vec (split-with odd? nil)))))
  (is (eq [[0 2] [3 4]] (jsv! '(mapv vec (split-with even? [0 2 3 4]))))))

(deftest count-test
  (is (= 3 (jsv! '(count "foo"))))
  (is (= 0 (jsv! '(count nil))))
  (is (= 0 (jsv! '(count []))))
  (is (= 3 (jsv! '(count [1 2 3]))))
  (is (= 3 (jsv! '(count (take 3 (range)))))))

(deftest logic-return
  (is (= 2 (jsv! '(do (defn foo [a b] (and a b)) (foo 1 2)))))
  (is (= 1 (jsv! '(do (defn foo [a b] (or a b)) (foo 1 2))))))

(deftest multiple-arity-infix
  (is (true? (jsv! '(> 5 4 3 2 1))))
  (is (true? (jsv! '(> 5 4 3))))
  (is (true? (jsv! '(> 5 4))))
  ;; I would say this is undefined in squint for now:
  #_(is (true? (jsv! '(> 5)))))

(deftest some?-test
  (is (jsv! '(some? 1)))
  (is (jsv! '(some? false)))
  (is (jsv! '(not (some? nil))))
  (is (jsv! '(not (some? js/undefined)))))

(deftest ns-test
  (is (str/includes? (compiler/compile-string (pr-str '(ns foo (:require ["./popup.css" ]))))
                     "import './popup.css'"))
  (is (re-find #"import.*'./popup.css'"
               (compiler/compile-string (pr-str '(ns foo (:require ["./popup.css" :as pop])))))))

(deftest letfn-test
  (is (= 3 (jsv! '(letfn [(f [x] (g x)) (g [x] (inc x))] (f 2)))))
  (is (= 20 (jsv! '(do (defn foo [] (letfn [(g [x] (f x)) (f [x] (* 2 x))] (g 10))) (foo))))))

(deftest refer-clojure-exclude-test
  (is (= "yolo" (jsv! '(do (ns foo (:refer-clojure :exclude [assoc])) (defn assoc [m x y] :yolo) (assoc {} :foo :bar))))))

(deftest double-names-in-sig-test
  (is (= 2 (jsv! '(do (defn foo [x x] x) (foo 1 2))))))

(defn init []
  (t/run-tests 'squint.compiler-test 'squint.jsx-test 'squint.string-test))
