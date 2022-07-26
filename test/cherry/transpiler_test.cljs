(ns cherry.transpiler-test
  (:require
   [cherry.transpiler :as cherry]
   [clojure.string :as str]
   [clojure.test :as t :refer [deftest is]]))

(aset js/globalThis "__destructure_map" cljs.core/--destructure-map)
(aset js/globalThis "vector" cljs.core/vector)
(aset js/globalThis "arrayMap" cljs.core/array-map)
(aset js/globalThis "keyword" cljs.core/keyword)
(aset js/globalThis "dissoc" cljs.core/dissoc)
(aset js/globalThis "get" cljs.core/get)
(aset js/globalThis "pos_QMARK_" cljs.core/pos?)
(aset js/globalThis "dec" cljs.core/dec)
(aset js/globalThis "seq" cljs.core/seq)
(aset js/globalThis "chunked_seq_QMARK_" cljs.core/chunked-seq?)
(aset js/globalThis "first" cljs.core/first)
(aset js/globalThis "prn" cljs.core/prn)
(aset js/globalThis "next" cljs.core/next)
(aset js/globalThis "truth_" cljs.core/truth_)
(aset js/globalThis "atom" cljs.core/atom)
(aset js/globalThis "swap_BANG_" cljs.core/swap!)
(aset js/globalThis "conj" cljs.core/conj)
(aset js/globalThis "deref" cljs.core/deref)
(aset js/globalThis "cons" cljs.core/cons)
(aset js/globalThis "rest" cljs.core/rest)
(aset js/globalThis "concat" cljs.core/concat)
(aset js/globalThis "LazySeq" cljs.core/LazySeq)
(aset js/globalThis "map" cljs.core/map)
(aset js/globalThis "re_seq" cljs.core/re-seq)
(aset js/globalThis "str" cljs.core/str)
(aset js/globalThis "symbol" cljs.core/symbol)
(aset js/globalThis "list" cljs.core/list)
(aset js/globalThis "_EQ_" cljs.core/=)

(defn jss! [expr]
  (if (string? expr)
    (cherry/transpile-string expr {:elide-imports true
                                   :elide-exports true})
    (cherry/transpile-form expr)))

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
    (is (= [1 2 3] (js/eval s))))
  (let [s (jss! "(do (def x (do 1 2 {:x 1 :y 2})) x)")]
    (is (= {:x 1 :y 2} (js/eval s))))
  (let [s (jss! "(do (def x (do 1 2 #js {:x 1 :y 2})) x)")]
    (is (= (str #js {:x 1 :y 2}) (str (js/eval s))))))

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
    (is (= '(1 1 1)
           (js/eval s))))
  (let [s (jss! '(let [name (fn [_] 1)
                       name (fn [_] 2)]
                   (map name [1 2 3])))]
    (is (= '(2 2 2)
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
    (is (= 1 ((js/eval s) 1)))))

(deftest defn-test
  (let [s (jss! '(do (defn f [x] x) f))]
    (is (= 1 ((js/eval s) 1))))
  (let [s (jss! '(do (defn f [x] (let [y 1] (+ x y))) f))]
    (is (= 2 ((js/eval s) 1))))
  (let [s (jss! '(do (defn foo [x]
                       (dissoc x :foo))
                     (foo {:a 1 :foo :bar})))]
    (is (= {:a 1} (js/eval s))))
  (let [s (jss! "(do (defn f [^js {:keys [a b c]}] (+ a b c)) f)")]
    (is (= 6 ((js/eval s) #js {:a 1 :b 2 :c 3}))))
  (let [s (jss! '(do (defn quux [x]
                       (if (pos? x)
                         1
                         2))
                     (quux 1)))]
    (is (= 1 (js/eval s)))))

(deftest defn-recur-test
  (let [s (jss! '(do (defn quux [x]
                       (if (pos? x)
                         (recur (dec x))
                         x))
                     (quux 1)))]
    (is (zero? (js/eval s)))))

(deftest loop-test
  (let [s (jss! '(loop [x 1] (+ 1 2 x)))]
    (is (= 4 (js/eval s))))
  (let [s (jss! '(loop [x 10]
                   (if (pos? x)
                     (recur (dec x))
                     x)))]
    (is (zero? (js/eval s)))))

(deftest if-test
  (is (true? (jsv! "(if 0 true false)")))
  (let [s (jss! "[(if false true false)]")]
    (false? (first (js/eval s))))
  (let [s (jss! "(let [x (if (inc 1) (inc 2) (inc 3))]
                   x)")]
    (is (= 3 (js/eval s))))
  (let [s (jss! "(let [x (do 1 (if (inc 1) (inc 2) (inc 3)))]
                   x)")]
    (is (= 3 (js/eval s)))))

(deftest doseq-test
  (let [s (jss! '(let [a (atom [])]
                   (doseq [x [1 2 3]]
                     (swap! a conj x))
                   (deref a)))]
    (is (= [1 2 3] (js/eval s))))
  (let [s (jss! '(let [a (atom [])]
                   (doseq [x [1 2 3]
                           y [4 5 6]]
                     (swap! a conj x y))
                   (deref a)))]
    (is (=  [1 4 1 5 1 6 2 4 2 5 2 6 3 4 3 5 3 6]
            (js/eval s)))))

(deftest for-test
  (let [s (jss! '(for [x [1 2 3] y [4 5 6]] [x y]))]
    (is (= '([1 4] [1 5] [1 6] [2 4] [2 5] [2 6] [3 4] [3 5] [3 6])
           (js/eval s)))))

(deftest regex-test
  (is (= '("foo" "foo")
         (jsv! '(re-seq #"foo" "foo foo")))))

(deftest new-test
  (is (= "hello" (jsv! '(str (js/String. "hello"))))))

(deftest quote-test
  (is (= '{x 1} (jsv! (list 'quote '{x 1}))))
  (is (= '(def x 1) (jsv! (list 'quote '(def x 1))))))

(deftest case-test
  (let [s (jss! '(case 'x x 2))]
    (is (= 2 (js/eval s))))
  (is (= 2 (jsv! '(case 1 :foo :bar 1 2))))
  (is (thrown-with-msg? js/Error #"No matching clause"
                        (jsv! '(case 'x y 2)))))
