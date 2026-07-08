(ns squint.immutable-test
  (:require
   [clojure.test]
   [squint.compiler :as compiler]
   #_:clj-kondo/ignore
   ["fs" :as fs]
   #_:clj-kondo/ignore
   ["path" :as path]
   ;; required by the `squint.eval-macro/deftest-eval` macro
   #_:clj-kondo/ignore
   ["url" :as url])
  (:require-macros [squint.eval-macro :refer [deftest-eval]]))

(defn compile! [str-or-expr]
  (let [s (if (string? str-or-expr)
            str-or-expr
            (pr-str str-or-expr))]
    (compiler/compile-string s)))

(def dyn-import (js/eval "(x) => import(x)"))

(deftest-eval basics-test
  (do (ns foo (:require [squint.immutable :as i]
                        [cljs.test :refer [is]]))
      (def m (i/hash-map :a 1 :b 2))
      (is (i/hash-map? m))
      (is (map? m))
      (is (associative? m))
      (is (= 2 (count m)))
      (is (= 1 (get m :a)))
      (is (= 1 (:a m)))
      (is (= 42 (get m :zz 42)))
      (is (nil? (get m :zz)))
      (is (contains? m :a))
      (is (not (contains? m :zz)))
      (is (= [:a 1] (find m :a)))
      (is (nil? (find m :zz)))
      ;; persistence
      (def m2 (assoc m :c 3))
      (is (= 2 (count m)))
      (is (= 3 (count m2)))
      (is (= 3 (get m2 :c)))
      (def m3 (dissoc m2 :a))
      (is (= 2 (count m3)))
      (is (nil? (get m3 :a)))
      (is (= 3 (count m2)))
      ;; assoc multiple pairs, dissoc multiple keys
      (is (= 4 (count (assoc m :c 3 :d 4))))
      (is (= 0 (count (dissoc m2 :a :b :c))))
      ;; empty keeps the type
      (is (i/hash-map? (empty m2)))
      (is (= 0 (count (empty m2))))
      ;; keys/vals/seq/first
      (is (= #{:a :b} (set (keys m))))
      (is (= #{1 2} (set (vals m))))
      (is (nil? (seq (i/hash-map))))
      (is (= [[:a 1]] (seq (i/hash-map :a 1))))
      (is (map-entry? (first (seq (i/hash-map :a 1)))))
      (is (= [:a 1] (first (i/hash-map :a 1))))))

(deftest-eval key-edge-cases-test
  (do (ns foo (:require [squint.immutable :as i]
                        [cljs.test :refer [is]]))
      ;; nil, false and 0 are distinct keys
      (def m (-> (i/hash-map) (assoc nil :nil-v) (assoc false :false-v) (assoc 0 :zero-v)))
      (is (= 3 (count m)))
      (is (= :nil-v (get m nil)))
      (is (= :false-v (get m false)))
      (is (= :zero-v (get m 0)))
      (is (contains? m nil))
      (is (= 2 (count (dissoc m nil))))
      (is (not (contains? (dissoc m nil) nil)))
      ;; numbers and their strings do not collide
      (is (nil? (get (i/hash-map 1 :one) "1")))
      ;; overwrite keeps count
      (is (= 1 (count (-> (i/hash-map :a 1) (assoc :a 2)))))
      (is (= 2 (get (-> (i/hash-map :a 1) (assoc :a 2)) :a)))))

(deftest-eval composite-keys-test
  (do (ns foo (:require [squint.immutable :as i]
                        [cljs.test :refer [is]]))
      (def m (-> (i/hash-map)
                 (assoc [1 2] :vec)
                 (assoc {:x 1} :map)
                 (assoc #{1 2} :set)))
      (is (= :vec (get m [1 2])))
      ;; a list and a lazy seq with equal elements hit the vector entry
      (is (= :vec (get m (list 1 2))))
      (is (= :vec (get m (map inc [0 1]))))
      (is (= :map (get m {:x 1})))
      (is (= :set (get m #{2 1})))
      (is (nil? (get m [2 1])))
      (is (= 2 (count (dissoc m [1 2]))))
      ;; a hamt map as key, hit with a plain map
      (def mk (assoc (i/hash-map) (i/hash-map :a 1) :found))
      (is (= :found (get mk {:a 1})))))

(deftest-eval hash-collision-test
  (do (ns foo (:require [squint.immutable :as i]
                        [cljs.test :refer [is]]))
      ;; "Aa" and "BB" have the same hash
      (is (= (hash "Aa") (hash "BB")))
      (def m (i/hash-map "Aa" 1 "BB" 2))
      (is (= 2 (count m)))
      (is (= 1 (get m "Aa")))
      (is (= 2 (get m "BB")))
      (def m2 (dissoc m "Aa"))
      (is (= 1 (count m2)))
      (is (nil? (get m2 "Aa")))
      (is (= 2 (get m2 "BB")))))

(deftest-eval equality-and-hash-test
  (do (ns foo (:require [squint.immutable :as i]
                        [cljs.test :refer [is]]))
      (is (= (i/hash-map :a 1) (i/hash-map :a 1)))
      (is (= (i/hash-map :a 1) {:a 1}))
      (is (= {:a 1} (i/hash-map :a 1)))
      (is (= (i/hash-map) {}))
      (is (not= (i/hash-map :a 1) {:a 2}))
      (is (not= (i/hash-map :a 1 :b 2) {:a 1}))
      (is (not= (i/hash-map :a 1) nil))
      (is (not= (i/hash-map :a 1) [[:a 1]]))
      ;; nested
      (is (= (i/hash-map :a {:b [1 2]}) {:a {:b [1 2]}}))
      ;; = implies same hash
      (is (= (hash (i/hash-map :a 1)) (hash {:a 1})))
      (is (= (hash [1 2 3]) (hash (list 1 2 3))))
      (is (= (hash {:a 1, :b 2}) (hash {:b 2, :a 1})))))

(deftest-eval core-fns-test
  (do (ns foo (:require [squint.immutable :as i]
                        [cljs.test :refer [is]]))
      (def m (i/hash-map :a 1 :b 2))
      ;; conj: entry vector, plain map, hamt map
      (is (= 9 (get (conj m [:k 9]) :k)))
      (is (= 4 (get (conj m {:d 4}) :d)))
      (is (= 3 (count (conj m (i/hash-map :e 5)))))
      ;; into preserves the type
      (def mi (into (i/hash-map) [[:a 1] [:b 2]]))
      (is (i/hash-map? mi))
      (is (= 2 (count mi)))
      (is (i/hash-map? (into (i/hash-map) (map (fn [x] [x x])) [1 2 3])))
      ;; merge keeps the hamt type, takes objects and hamt maps
      (def mm (merge (i/hash-map :a 1) {:b 2} (i/hash-map :c 3)))
      (is (i/hash-map? mm))
      (is (= {:a 1 :b 2 :c 3} mm))
      (is (= {:a 4 :b 2} (merge-with + (i/hash-map :a 1) {:a 3 :b 2})))
      ;; update / assoc-in / update-in / get-in
      (is (= 2 (get (update m :a inc) :a)))
      (def nested (i/hash-map :x (i/hash-map :y 1)))
      (is (= 1 (get-in nested [:x :y])))
      (is (= 2 (get-in (assoc-in nested [:x :y] 2) [:x :y])))
      (is (= 2 (get-in (update-in nested [:x :y] inc) [:x :y])))
      (is (= 5 (get-in nested [:x :zz] 5)))
      ;; select-keys
      (is (= {:a 1} (select-keys m [:a :zz])))
      ;; reduce-kv, including early exit
      (is (= 3 (reduce-kv (fn [acc _k v] (+ acc v)) 0 m)))
      (is (= :early (reduce-kv (fn [_ _ _] (reduced :early)) 0 m)))
      ;; seq-level fns and destructuring
      (is (= #{[:a 1] [:b 2]} (set (map identity m))))
      (is (= #{:a :b} (set (for [[k _v] m] k))))
      (is (= 3 (reduce (fn [acc [_k v]] (+ acc v)) 0 m)))
      (is (= 2 (count (filter (fn [[_k v]] (pos? v)) m))))))

(deftest-eval transient-test
  (do (ns foo (:require [squint.immutable :as i]
                        [cljs.test :refer [is]]))
      (def t (transient (i/hash-map :a 1)))
      (assoc! t :b 2)
      (conj! t [:c 3])
      (dissoc! t :a)
      (is (= 2 (count t)))
      (is (= 2 (get t :b)))
      (def p (persistent! t))
      (is (i/hash-map? p))
      (is (= {:b 2 :c 3} p))
      (is (thrown? js/Error (assoc! t :d 4)))
      ;; the source map is untouched
      (def src (i/hash-map :a 1))
      (persistent! (assoc! (transient src) :b 2))
      (is (= {:a 1} src))))

(deftest-eval print-test
  (do (ns foo (:require [squint.immutable :as i]
                        [cljs.test :refer [is]]))
      (is (= "{:a 1}" (pr-str (i/hash-map :a 1))))
      (is (= "{[1 2] \"v\"}" (pr-str (i/hash-map [1 2] "v"))))
      (is (= "{:a {:b 1}}" (pr-str (i/hash-map :a (i/hash-map :b 1)))))))

(deftest-eval js-interop-test
  (do (ns foo (:require [squint.immutable :as i]
                        [cljs.test :refer [is]]))
      ;; clj->js via IEncodeJS: deep plain-object snapshot
      (def o (clj->js (i/hash-map :a (i/hash-map :b [1 2]))))
      (is (= js/Object (.-constructor o)))
      (is (= js/Object (.-constructor (.-a o))))
      (is (= 1 (aget (.-b (.-a o)) 0)))
      ;; composite keys are stringified like pr-str
      (is (= "v" (aget (clj->js (i/hash-map [3 4] "v")) "[3 4]")))
      ;; obj-view: live read-only facade
      (def v (i/obj-view (i/hash-map :a 1 :b 2)))
      (is (= 1 (.-a v)))
      (is (= ["a" "b"] (sort (js/Object.keys v))))
      (is (= "{\"a\":1,\"b\":2}" (js/JSON.stringify v)))
      (is (thrown? js/Error (aset v "x" 1)))))

(deftest-eval protocols-test
  (do (ns foo (:require [squint.immutable :as i]
                        [cljs.test :refer [is]]))
      (def m (i/hash-map :a 1))
      (is (satisfies? IMap m))
      (is (satisfies? ILookup m))
      (is (satisfies? IHash m))
      (is (satisfies? IEncodeJS m))
      ;; a custom type can opt into hashing via IHash and act as a map key
      (deftype Wrapper [v])
      (extend-type Wrapper
        IHash
        (-hash [w] (hash (.-v w))))
      (def wm (-> (i/hash-map)
                  (assoc (->Wrapper [1 2]) :first)
                  (assoc (->Wrapper [1 2]) :second)))
      (is (= 1 (count wm)))
      (is (= :second (get wm (->Wrapper [1 2]))))))

;; the cljc-portable opt-in: refer hash-map from squint.immutable; on
;; CLJS/JVM the conditionals vanish and core hash-map is already persistent.
;; A string program: the :squint conditionals must not hit the CLJS reader
;; compiling this file.
(deftest-eval refer-pattern-test
  "(ns foo
     #?(:squint (:refer-clojure :exclude [hash-map]))
     #?(:squint (:require [squint.immutable :as i :refer [hash-map]]))
     (:require [cljs.test :refer [is]]))
   (is (= 2 (get (hash-map 1 2 3 4) 1)))
   (is (i/hash-map? (hash-map)))
   (is (i/hash-map? (apply hash-map [1 2 3 4])))
   (is (= 4 (get (apply hash-map [1 2 3 4]) 3)))
   (is (map? (hash-map 1 2)))
   (is (= 3 (count (assoc (hash-map 1 2) 3 4 5 6))))
   (is (= (hash-map 1 2) (hash-map 1 2)))
   (is (not (i/hash-map? {})))")

(deftest-eval vector-basics-test
  (do (ns foo (:require [squint.immutable :as i]
                        [cljs.test :refer [is]]))
      (def v (i/vector 1 2 3))
      (is (i/vector? v))
      (is (vector? v))
      (is (sequential? v))
      (is (= 3 (count v)))
      (is (= 1 (nth v 0)))
      (is (= :dflt (nth v 9 :dflt)))
      (is (= 2 (get v 1)))
      (is (= 42 (get v 9 42)))
      (is (thrown? js/Error (nth v 9)))
      ;; persistence
      (def v2 (conj v 4))
      (is (= 3 (count v)))
      (is (= [1 2 3 4] v2))
      (is (= [1 :x 3] (assoc v 1 :x)))
      (is (= [1 2 3 :end] (assoc v 3 :end)))
      (is (thrown? js/Error (assoc v 5 :oob)))
      (is (contains? v 2))
      (is (not (contains? v 3)))
      ;; peek/pop/subvec
      (is (= 3 (peek v)))
      (is (= [1 2] (pop v)))
      (is (= [2 3] (subvec v 1)))
      (is (= [2] (subvec v 1 2)))
      (is (i/vector? (subvec v 1)))
      ;; empty keeps the type
      (is (i/vector? (empty v)))
      (is (= 0 (count (empty v))))))

(deftest-eval vector-equality-test
  (do (ns foo (:require [squint.immutable :as i]
                        [cljs.test :refer [is]]))
      (def v (i/vector 1 2 3))
      (is (= v [1 2 3]))
      (is (= [1 2 3] v))
      (is (= v (i/vector 1 2 3)))
      (is (= v (list 1 2 3)))
      (is (= v (map inc [0 1 2])))
      (is (not= v [1 2]))
      (is (not= v #{1 2 3}))
      (is (= (hash v) (hash [1 2 3])))
      ;; pvec as a hamt map key, hit with a plain vector
      (is (= :hit (get (assoc (i/hash-map) (i/vector 1 2) :hit) [1 2])))
      ;; pvec entry conj on a hamt map
      (is (= 9 (get (conj (i/hash-map) (i/vector :k 9)) :k)))
      ;; nested equality across reps
      (is (= (i/vector 1 (i/hash-map :a (i/vector 2))) [1 {:a [2]}]))))

(deftest-eval vector-core-fns-test
  (do (ns foo (:require [squint.immutable :as i]
                        [cljs.test :refer [is]]))
      (def v (i/vector 1 2 3))
      (is (= [2 3 4] (mapv inc v)))
      (is (= 6 (reduce + 0 v)))
      (is (= 9 (reduce-kv (fn [acc i x] (+ acc i x)) 0 v)))
      (is (= :early (reduce-kv (fn [_ _ _] (reduced :early)) 0 v)))
      (is (= 1 (first v)))
      (is (= [2 3] (vec (rest v))))
      (is (= 3 (last v)))
      (is (= [1 2 3] (into [] v)))
      (is (i/vector? (into (i/vector) [1 2])))
      (is (= 3 (count (into (i/vector 1) [2 3]))))
      (is (= [[0 1] [1 2] [2 3]] (map-indexed vector v)))
      (is (= [1 2] (filter (fn [x] (< x 3)) v)))
      (is (= [1 :x 3] (update v 1 (fn [_] :x))))
      (is (= "[1 2 3]" (pr-str v)))
      (is (= "[[1] {:a 1}]" (pr-str (i/vector (i/vector 1) (i/hash-map :a 1)))))
      ;; clj->js: deep array
      (def a (clj->js (i/vector 1 (i/vector 2))))
      (is (js/Array.isArray a))
      (is (js/Array.isArray (aget a 1)))
      ;; i/vec conversions
      (is (i/vector? (i/vec [1 2])))
      (is (= [1 2] (i/vec [1 2])))
      (is (= 3 (count (i/vec (range 3)))))))

(deftest-eval vector-transient-test
  (do (ns foo (:require [squint.immutable :as i]
                        [cljs.test :refer [is]]))
      (def t (transient (i/vector 1)))
      (conj! t 2)
      (assoc! t 0 :x)
      (pop! t)
      (def p (persistent! t))
      (is (i/vector? p))
      (is (= [:x] p))
      (is (thrown? js/Error (conj! t 3)))
      (def src (i/vector 1))
      (persistent! (conj! (transient src) 2))
      (is (= [1] src))))

(deftest-eval vector-scale-test
  (do (ns foo (:require [squint.immutable :as i]
                        [cljs.test :refer [is]]))
      ;; through the tail, root overflow and back down
      (def n 3000)
      (def big (reduce conj (i/vector) (range n)))
      (is (= n (count big)))
      (is (every? (fn [x] (= x (nth big x))) (range n)))
      (is (= :mid (nth (assoc big 1500 :mid) 1500)))
      (is (= (/ (* n (dec n)) 2) (reduce + 0 big)))
      (def shrunk (reduce (fn [v _] (pop v)) big (range n)))
      (is (= 0 (count shrunk)))
      (is (= shrunk (i/vector)))))

(deftest-eval scale-test
  (do (ns foo (:require [squint.immutable :as i]
                        [cljs.test :refer [is]]))
      ;; enough keys to spill bitmap nodes into array nodes and pack back
      (def n 5000)
      (def big (reduce (fn [m i] (assoc m (str "k" i) i)) (i/hash-map) (range n)))
      (is (= n (count big)))
      (is (every? (fn [i] (= i (get big (str "k" i)))) (range n)))
      (is (nil? (get big "nope")))
      (is (= n (count (vec big))))
      (is (= (/ (* n (dec n)) 2) (reduce-kv (fn [acc _k v] (+ acc v)) 0 big)))
      (def shrunk (reduce (fn [m i] (dissoc m (str "k" i))) big (range n)))
      (is (= 0 (count shrunk)))
      (is (= shrunk (i/hash-map)))))
