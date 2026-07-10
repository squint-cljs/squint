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
      ;; nil values do not hide entries from find
      (is (= [:n nil] (find (i/hash-map :n nil) :n)))
      (is (some? (find (i/hash-map :n js/undefined) :n)))
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
      ;; persistent values key by value
      (def m (-> (i/hash-map)
                 (assoc (i/vector 1 2) :vec)
                 (assoc (i/hash-map :x 1) :map)
                 (assoc (i/hash-set 1 2) :set)))
      (is (= :vec (get m (i/vector 1 2))))
      (is (= :map (get m (i/hash-map :x 1))))
      (is (= :set (get m (i/hash-set 2 1))))
      (is (nil? (get m (i/vector 2 1))))
      (is (= 2 (count (dissoc m (i/vector 1 2)))))
      ;; plain data keys by reference, stable under mutation
      (def ra [1 2])
      (def mr (assoc (i/hash-map) ra :ref))
      (is (= :ref (get mr ra)))
      (is (nil? (get mr [1 2])))
      (.push ra 3)
      (is (= :ref (get mr ra)))
      (def mk (assoc (i/hash-map) (i/hash-map :a 1) :found))
      (is (= :found (get mk (i/hash-map :a 1))))))

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
      ;; equiv: a persistent map never equals plain data
      (is (not= (i/hash-map :a 1) {:a 1}))
      (is (not= {:a 1} (i/hash-map :a 1)))
      (is (not= (i/hash-map) {}))
      (is (not= (i/hash-map :a 1) (i/hash-map :a 2)))
      (is (not= (i/hash-map :a 1 :b 2) (i/hash-map :a 1)))
      (is (not= (i/hash-map :a 1) nil))
      ;; nested persistent values compare by value, plain values by reference
      (is (= (i/hash-map :a (i/vector 1)) (i/hash-map :a (i/vector 1))))
      (let [o {:x 1}]
        (is (= (i/hash-map :a o) (i/hash-map :a o)))
        (is (not= (i/hash-map :a {:x 1}) (i/hash-map :a {:x 1}))))
      ;; equiv implies same hash
      (is (= (hash (i/hash-map :a 1)) (hash (i/hash-map :a 1))))
      (is (= (hash (i/vector 1 2)) (hash (i/vector 1 2))))
      (is (not= (hash [1 2]) (hash [1 2])))))

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
      (is (= (i/hash-map :a 1 :b 2 :c 3) mm))
      (is (= (i/hash-map :a 4 :b 2) (merge-with + (i/hash-map :a 1) {:a 3 :b 2})))
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
      (is (= (i/hash-map :b 2 :c 3) p))
      (is (thrown? js/Error (assoc! t :d 4)))
      ;; the source map is untouched
      (def src (i/hash-map :a 1))
      (persistent! (assoc! (transient src) :b 2))
      (is (= (i/hash-map :a 1) src))))

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
      ;; a custom value type implements IEquiv and IHash together
      (deftype Wrapper [v])
      (extend-type Wrapper
        IEquiv
        (-equiv [w other] (and (instance? Wrapper other) (= (.-v w) (.-v other))))
        IHash
        (-hash [w] (hash (.-v w))))
      (def wm (-> (i/hash-map)
                  (assoc (->Wrapper (i/vector 1 2)) :first)
                  (assoc (->Wrapper (i/vector 1 2)) :second)))
      (is (= 1 (count wm)))
      (is (= :second (get wm (->Wrapper (i/vector 1 2)))))))

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
      (is (= (i/vector 1 2 3 4) v2))
      (is (= (i/vector 1 :x 3) (assoc v 1 :x)))
      (is (= (i/vector 1 2 3 :end) (assoc v 3 :end)))
      (is (thrown? js/Error (assoc v 5 :oob)))
      (is (contains? v 2))
      (is (not (contains? v 3)))
      ;; peek/pop/subvec
      (is (= 3 (peek v)))
      (is (= (i/vector 1 2) (pop v)))
      (is (= (i/vector 2 3) (subvec v 1)))
      (is (= (i/vector 2) (subvec v 1 2)))
      (is (i/vector? (subvec v 1)))
      ;; empty keeps the type
      (is (i/vector? (empty v)))
      (is (= 0 (count (empty v))))))

(deftest-eval vector-equality-test
  (do (ns foo (:require [squint.immutable :as i]
                        [cljs.test :refer [is]]))
      (def v (i/vector 1 2 3))
      (is (= v (i/vector 1 2 3)))
      ;; equiv: a pvec never equals plain data
      (is (not= v [1 2 3]))
      (is (not= [1 2 3] v))
      (is (not= v (list 1 2 3)))
      (is (not= v (i/vector 1 2)))
      (is (= (hash v) (hash (i/vector 1 2 3))))
      (is (not= (hash v) (hash [1 2 3])))
      ;; pvec as a hamt map key, hit with an equal pvec
      (is (= :hit (get (assoc (i/hash-map) (i/vector 1 2) :hit) (i/vector 1 2))))
      ;; pvec entry conj on a hamt map
      (is (= 9 (get (conj (i/hash-map) (i/vector :k 9)) :k)))
      ;; nested persistent equality
      (is (= (i/vector 1 (i/hash-map :a (i/vector 2)))
             (i/vector 1 (i/hash-map :a (i/vector 2)))))))

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
      (is (= (i/vector 1 :x 3) (update v 1 (fn [_] :x))))
      (is (= "[1 2 3]" (pr-str v)))
      (is (= "[[1] {:a 1}]" (pr-str (i/vector (i/vector 1) (i/hash-map :a 1)))))
      ;; clj->js: deep array
      (def a (clj->js (i/vector 1 (i/vector 2))))
      (is (js/Array.isArray a))
      (is (js/Array.isArray (aget a 1)))
      ;; i/vec conversions
      (is (i/vector? (i/vec [1 2])))
      (is (= (i/vector 1 2) (i/vec [1 2])))
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
      (is (= (i/vector :x) p))
      (is (thrown? js/Error (conj! t 3)))
      (def src (i/vector 1))
      (persistent! (conj! (transient src) 2))
      (is (= (i/vector 1) src))))

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

(deftest-eval set-basics-test
  (do (ns foo (:require [squint.immutable :as i]
                        [cljs.test :refer [is]]))
      (def s (i/hash-set 1 2 3))
      (is (i/hash-set? s))
      (is (set? s))
      (is (= 3 (count s)))
      (is (contains? s 2))
      (is (not (contains? s 9)))
      (is (= 2 (get s 2)))
      (is (= :nf (get s 9 :nf)))
      (is (= 2 (count (i/hash-set 1 1 2))))
      ;; conj/disj persistence
      (def s2 (conj s 4))
      (is (= 3 (count s)))
      (is (= 4 (count s2)))
      (is (= 3 (count (conj s 2))))
      (is (= 2 (count (disj s 1))))
      (is (not (contains? (disj s 1) 1)))
      ;; persistent elements by value, plain data by reference
      (def cs (i/hash-set (i/vector 1 2)))
      (is (contains? cs (i/vector 1 2)))
      (is (= 1 (count (conj cs (i/vector 1 2)))))
      (def ra [1 2])
      (def rs (i/hash-set ra))
      (is (contains? rs ra))
      (is (not (contains? rs [1 2])))
      (is (= 2 (count (conj rs [1 2]))))
      ;; empty keeps the type
      (is (i/hash-set? (empty s)))
      (is (= 0 (count (empty s))))))

(deftest-eval set-equality-test
  (do (ns foo (:require [squint.immutable :as i]
                        [cljs.test :refer [is]]))
      (is (= (i/hash-set 1 2) (i/hash-set 2 1)))
      ;; equiv: a pset never equals plain data
      (is (not= (i/hash-set 1 2) #{1 2}))
      (is (not= #{1 2} (i/hash-set 1 2)))
      (is (not= (i/hash-set 1 2) (i/hash-set 1)))
      (is (not= (i/hash-set 1 2) [1 2]))
      (is (= (hash (i/hash-set 1 2)) (hash (i/hash-set 2 1))))
      ;; pset as a hamt map key, hit with an equal pset
      (is (= :v (get (assoc (i/hash-map) (i/hash-set 1 2) :v) (i/hash-set 2 1))))
      ;; seq of a pset is sequential, empty seqs to nil
      (is (sequential? (seq (i/hash-set 1))))
      (is (nil? (seq (i/hash-set))))))

(deftest-eval set-core-fns-test
  (do (ns foo (:require [squint.immutable :as i]
                        [cljs.test :refer [is]]))
      (def s (i/hash-set 1 2 3))
      (is (= 6 (reduce + 0 s)))
      (is (= #{2 3 4} (set (map inc s))))
      (is (i/hash-set? (into (i/hash-set) (map inc) [1 2])))
      (is (= 3 (count (into (i/hash-set) [1 2 2 3]))))
      (is (i/hash-set? (into (i/hash-set) [1])))
      (is (= 2 (count (i/set [1 2 2]))))
      (is (= "#{\"a\"}" (pr-str (i/hash-set "a"))))
      (is (js/Array.isArray (clj->js (i/hash-set 1))))
      ;; transient
      (def t (transient (i/hash-set 1)))
      (conj! t 2)
      (disj! t 1)
      (def p (persistent! t))
      (is (i/hash-set? p))
      (is (= p (i/hash-set 2)))
      (is (thrown? js/Error (conj! t 3)))))

(deftest-eval clojure-set-test
  (do (ns foo (:require [squint.immutable :as i]
                        [clojure.set :as cset]
                        [cljs.test :refer [is]]))
      ;; set ops preserve the persistent type and work across reps
      (is (= (i/hash-set 1 2 3) (cset/union (i/hash-set 1 2) (i/hash-set 2 3))))
      (is (i/hash-set? (cset/union (i/hash-set 1 2) (i/hash-set 3))))
      (is (= (i/hash-set 2) (cset/intersection (i/hash-set 1 2) (i/hash-set 2 3))))
      (is (i/hash-set? (cset/intersection (i/hash-set 1 2) (i/hash-set 2))))
      (is (= (i/hash-set 1) (cset/difference (i/hash-set 1 2) (i/hash-set 2))))
      (is (cset/subset? (i/hash-set 1) (i/hash-set 1 2)))
      (is (cset/superset? (i/hash-set 1 2) (i/hash-set 1)))
      (is (= (i/hash-set 1 2 3) (cset/union (i/hash-set 1 2) #{3})))
      (is (= (i/hash-set 2) (cset/select even? (i/hash-set 1 2 3))))
      (is (i/hash-set? (cset/select even? (i/hash-set 2))))
      ;; persistent composite elements
      (is (= (i/hash-set (i/vector 1 2))
             (cset/intersection (i/hash-set (i/vector 1 2) (i/vector 3))
                                (i/hash-set (i/vector 1 2)))))
      ;; js sets keep the old behavior
      (is (= #{1 2 3} (cset/union #{1 2} #{3})))
      ;; map ops preserve the hamt type (previously corrupted via assoc!)
      (def hm (i/hash-map :a 1 :b 2))
      (is (= (i/hash-map :x 1 :b 2) (cset/rename-keys hm {:a :x})))
      (is (i/hash-map? (cset/rename-keys hm {:a :x})))
      (is (= (i/hash-map :a 1 :b 2) hm))
      (is (= (i/hash-map 1 :a 2 :b) (cset/map-invert hm)))
      (is (i/hash-map? (cset/map-invert hm)))
      (is (= {:x 1} (cset/rename-keys {:a 1} {:a :x})))
      ;; project/rename preserve the set type; elements are plain
      ;; select-keys maps, so assert contents, not whole-set equality
      (def rel (i/hash-set (i/hash-map :a 1 :b 2)))
      (def proj (cset/project rel :a))
      (is (i/hash-set? proj))
      (is (= 1 (count proj)))
      (is (= [{:a 1}] (vec (seq proj))))))

(deftest-eval metadata-test
  (do (ns foo (:require [squint.immutable :as i]
                        [cljs.test :refer [is]]))
      (def m (with-meta (i/hash-map :a 1) {:x 1}))
      (is (= {:x 1} (meta m)))
      (is (nil? (meta (i/hash-map :a 1))))
      ;; value ops carry metadata, like CLJS
      (is (= {:x 1} (meta (assoc m :b 2))))
      (is (= {:x 1} (meta (dissoc m :a))))
      (is (= {:x 1} (meta (conj m [:c 3]))))
      (is (= {:x 1} (meta (empty m))))
      (is (= {:x 1 :y 2} (meta (vary-meta m assoc :y 2))))
      ;; equality and hashing ignore metadata
      (is (= m (i/hash-map :a 1)))
      (is (= (hash m) (hash (i/hash-map :a 1))))
      ;; with-meta replaces, does not merge
      (is (= {:z 9} (meta (with-meta m {:z 9}))))
      ;; vector and set
      (def v (with-meta (i/vector 1 2) {:v 1}))
      (is (= {:v 1} (meta (conj v 3))))
      (is (= {:v 1} (meta (assoc v 0 9))))
      (is (= {:v 1} (meta (pop (pop v)))))
      (def s (with-meta (i/hash-set 1) {:s 1}))
      (is (= {:s 1} (meta (conj s 2))))
      (is (= {:s 1} (meta (disj s 1))))
      ;; plain collections still carry metadata through the instance scheme
      (is (= {:p 1} (meta (with-meta {} {:p 1}))))
      (is (= {:p 1} (meta (conj (with-meta [1] {:p 1}) 2))))))

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
