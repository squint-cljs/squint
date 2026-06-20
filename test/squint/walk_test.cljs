(ns squint.walk-test
  "Tests for squint's clojure.walk, compiled and run through the real squint
  compiler via `evalll`. Adapted from clojure's test_clojure/clojure_walk.clj
  and clojurescript's clojure/walk_test.cljs, accounting for squint semantics
  (built-in mutable data structures; keywords are strings)."
  (:require
   [clojure.test :as t :refer [deftest]]
   [squint.compiler :as compiler]
   [squint.test-utils :refer [eq]]
   #_:clj-kondo/ignore
   ["fs" :as fs]
   #_:clj-kondo/ignore
   ["path" :as path]
   ["url" :as url])
  (:require-macros [squint.eval-macro :refer [evalll]]))

(defn compile! [str-or-expr]
  (let [s (if (string? str-or-expr)
            str-or-expr
            (pr-str str-or-expr))]
    (compiler/compile-string s)))

(def dyn-import (js/eval "(x) => import(x)"))

;; --- basic transformation (clojurescript walk_test) ---

(deftest postwalk-test
  ;; A js/Map preserves the numeric key 0 as a number (a squint object literal
  ;; would coerce it to the string "0"), so inc-leaf increments the key 0 -> 1
  ;; just like clojure/clojurescript. Compared with `=` since clj->js does not
  ;; round-trip Map keys.
  (evalll (eq true)
          '(do (ns foo (:require [clojure.walk :as w]))
               (defn inc-leaf [x] (if (number? x) (inc x) x))
               (def result
                 (= [2 (js/Map. #js [#js [1 "1"] #js [:two 2]])]
                    (w/postwalk inc-leaf [1 (js/Map. #js [#js [0 "1"] #js [:two 1]])]))))))

(deftest prewalk-test
  (evalll (eq [2 [3 4] {:a 5}])
          '(do (ns foo (:require [clojure.walk :as w]))
               (defn inc-leaf [x] (if (number? x) (inc x) x))
               (def result (w/prewalk inc-leaf [1 [2 3] {:a 4}])))))

;; --- traversal order (clojure clojure_walk) ---

(deftest prewalk-order-test
  (evalll (eq [[1 2 {:a 3} (list 4 [5])]
               1 2 {:a 3} [:a 3] :a 3 (list 4 [5])
               4 [5] 5])
          '(do (ns foo (:require [clojure.walk :as w]))
               (def result
                 (let [a (atom [])]
                   (w/prewalk (fn [form] (swap! a conj form) form)
                              [1 2 {:a 3} (list 4 [5])])
                   @a)))))

(deftest postwalk-order-test
  (evalll (eq [1 2
               :a 3 [:a 3] {:a 3}
               4 5 [5] (list 4 [5])
               [1 2 {:a 3} (list 4 [5])]])
          '(do (ns foo (:require [clojure.walk :as w]))
               (def result
                 (let [a (atom [])]
                   (w/postwalk (fn [form] (swap! a conj form) form)
                               [1 2 {:a 3} (list 4 [5])])
                   @a)))))

;; --- replace (clojure clojure_walk), scalar keys only ---
;; squint maps key by JS string coercion, so collection-valued keys/lookups
;; (e.g. {:a {:a :a}}) can't be distinguished; scalar keys behave like clojure.

(deftest prewalk-replace-test
  (evalll (eq [:b :b (list 3 :c :b)])
          '(do (ns foo (:require [clojure.walk :as w]))
               (def result (w/prewalk-replace {:a :b} [:a :a (list 3 :c :a)])))))

(deftest postwalk-replace-test
  (evalll (eq [:b :b (list 3 :c :b)])
          '(do (ns foo (:require [clojure.walk :as w]))
               (def result (w/postwalk-replace {:a :b} [:a :a (list 3 :c :a)])))))

;; --- walk returns correct value and type per collection (clojure walk) ---

(deftest walk-list-test
  (evalll (eq 9)
          '(do (ns foo (:require [clojure.walk :as w]))
               (def result (w/walk inc #(reduce + %) (list 1 2 3))))))

(deftest walk-vector-test
  (evalll (eq 9)
          '(do (ns foo (:require [clojure.walk :as w]))
               (def result (w/walk inc #(reduce + %) [1 2 3])))))

(deftest walk-set-test
  (evalll (eq 9)
          '(do (ns foo (:require [clojure.walk :as w]))
               (def result (w/walk inc #(reduce + %) #{1 2 3})))))

(deftest walk-map-test
  (evalll (eq 9)
          '(do (ns foo (:require [clojure.walk :as w]))
               (def result (w/walk (fn [[k v]] [k (inc v)])
                                    #(reduce + (vals %))
                                    {:a 1 :b 2 :c 3})))))

(deftest walk-preserves-collection-type-test
  (evalll (eq true)
          '(do (ns foo (:require [clojure.walk :as w]))
               (def result (and (vector? (w/postwalk identity [1 2 3]))
                                 (set? (w/postwalk identity #{1 2 3}))
                                 (map? (w/postwalk identity {:a 1}))
                                 (list? (w/postwalk identity (list 1 2 3))))))))

;; --- keywordize-keys / stringify-keys ---
;; in squint keywords are strings, so these normalize keys via `name` (identity
;; for keyword keys); the test exercises recursive traversal over nested maps.

(deftest stringify-keys-test
  (evalll (eq {:a 1, :b {:c 2}})
          '(do (ns foo (:require [clojure.walk :as w]))
               (def result (w/stringify-keys {:a 1 :b {:c 2}})))))

(deftest keywordize-keys-test
  (evalll (eq {:a 1, :b {:c 2}})
          '(do (ns foo (:require [clojure.walk :as w]))
               (def result (w/keywordize-keys {:a 1 :b {:c 2}})))))

;; --- demo fns: should not throw and return the walked form unchanged ---

(deftest demo-fns-test
  (evalll (eq [[1 2] [1 2]])
          '(do (ns foo (:require [clojure.walk :as w]))
               (def result [(w/postwalk-demo [1 2]) (w/prewalk-demo [1 2])]))))

;; --- metadata preservation (clojure retain-meta / clojurescript preserves-meta) ---

(deftest postwalk-preserves-meta-test
  (evalll (eq {:foo 3})
          '(do (ns foo (:require [clojure.walk :as w]))
               (def result (-> (w/postwalk identity [1 (with-meta [1 2] {:foo 3})])
                               (nth 1)
                               meta)))))

(deftest prewalk-preserves-meta-test
  (evalll (eq {:foo 3})
          '(do (ns foo (:require [clojure.walk :as w]))
               (def result (-> (w/prewalk identity [1 (with-meta [1 2] {:foo 3})])
                               (nth 1)
                               meta)))))

(deftest walk-retains-meta-on-root-test
  (evalll (eq [true true true true])
          '(do (ns foo (:require [clojure.walk :as w]))
               (def result
                 (let [m {:foo true}]
                   [(= m (meta (w/postwalk identity (with-meta (list 1 2) m))))
                    (= m (meta (w/postwalk identity (with-meta [1 2] m))))
                    (= m (meta (w/postwalk identity (with-meta #{1 2} m))))
                    (= m (meta (w/postwalk identity (with-meta {1 2} m))))])))))
