(ns squint.walk-test
  "Tests for squint's clojure.walk, compiled and run through the real squint
  compiler via `deftest-eval`. Adapted from clojure's test_clojure/clojure_walk.clj
  and clojurescript's clojure/walk_test.cljs, accounting for squint semantics
  (built-in mutable data structures; keywords are strings)."
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

;; --- basic transformation (clojurescript walk_test) ---

(deftest-eval postwalk-test
  ;; A js/Map preserves the numeric key 0 as a number (a squint object literal
  ;; would coerce it to the string "0"), so inc-leaf increments the key 0 -> 1
  ;; just like clojure/clojurescript.
  (do (ns foo (:require [clojure.walk :as w]
                        [cljs.test :refer [is]]))
      (defn inc-leaf [x] (if (number? x) (inc x) x))
      (is (= [2 (js/Map. #js [#js [1 "1"] #js [:two 2]])]
             (w/postwalk inc-leaf [1 (js/Map. #js [#js [0 "1"] #js [:two 1]])])))))

(deftest-eval prewalk-test
  (do (ns foo (:require [clojure.walk :as w]
                        [cljs.test :refer [is]]))
      (defn inc-leaf [x] (if (number? x) (inc x) x))
      (is (= [2 [3 4] {:a 5}] (w/prewalk inc-leaf [1 [2 3] {:a 4}])))))

;; --- traversal order (clojure clojure_walk) ---

(deftest-eval prewalk-order-test
  (do (ns foo (:require [clojure.walk :as w]
                        [cljs.test :refer [is]]))
      (is (= [[1 2 {:a 3} (list 4 [5])]
              1 2 {:a 3} [:a 3] :a 3 (list 4 [5])
              4 [5] 5]
             (let [a (atom [])]
               (w/prewalk (fn [form] (swap! a conj form) form)
                          [1 2 {:a 3} (list 4 [5])])
               @a)))))

(deftest-eval postwalk-order-test
  (do (ns foo (:require [clojure.walk :as w]
                        [cljs.test :refer [is]]))
      (is (= [1 2
              :a 3 [:a 3] {:a 3}
              4 5 [5] (list 4 [5])
              [1 2 {:a 3} (list 4 [5])]]
             (let [a (atom [])]
               (w/postwalk (fn [form] (swap! a conj form) form)
                           [1 2 {:a 3} (list 4 [5])])
               @a)))))

;; --- replace (clojure clojure_walk), scalar keys only ---
;; squint maps key by JS string coercion, so collection-valued keys/lookups
;; (e.g. {:a {:a :a}}) can't be distinguished; scalar keys behave like clojure.

(deftest-eval prewalk-replace-test
  (do (ns foo (:require [clojure.walk :as w]
                        [cljs.test :refer [is]]))
      (is (= [:b :b (list 3 :c :b)] (w/prewalk-replace {:a :b} [:a :a (list 3 :c :a)])))))

(deftest-eval postwalk-replace-test
  (do (ns foo (:require [clojure.walk :as w]
                        [cljs.test :refer [is]]))
      (is (= [:b :b (list 3 :c :b)] (w/postwalk-replace {:a :b} [:a :a (list 3 :c :a)])))))

;; --- walk returns correct value and type per collection (clojure walk) ---

(deftest-eval walk-list-test
  (do (ns foo (:require [clojure.walk :as w]
                        [cljs.test :refer [is]]))
      (is (= 9 (w/walk inc #(reduce + %) (list 1 2 3))))))

(deftest-eval walk-vector-test
  (do (ns foo (:require [clojure.walk :as w]
                        [cljs.test :refer [is]]))
      (is (= 9 (w/walk inc #(reduce + %) [1 2 3])))))

(deftest-eval walk-set-test
  (do (ns foo (:require [clojure.walk :as w]
                        [cljs.test :refer [is]]))
      (is (= 9 (w/walk inc #(reduce + %) #{1 2 3})))))

(deftest-eval walk-map-test
  (do (ns foo (:require [clojure.walk :as w]
                        [cljs.test :refer [is]]))
      (is (= 9 (w/walk (fn [[k v]] [k (inc v)])
                       #(reduce + (vals %))
                       {:a 1 :b 2 :c 3})))))

(deftest-eval walk-preserves-collection-type-test
  (do (ns foo (:require [clojure.walk :as w]
                        [cljs.test :refer [is]]))
      (is (and (vector? (w/postwalk identity [1 2 3]))
               (set? (w/postwalk identity #{1 2 3}))
               (map? (w/postwalk identity {:a 1}))
               (list? (w/postwalk identity (list 1 2 3)))))))

;; --- keywordize-keys / stringify-keys ---
;; in squint keywords are strings, so these normalize keys via `name` (identity
;; for keyword keys); the test exercises recursive traversal over nested maps.

(deftest-eval stringify-keys-test
  (do (ns foo (:require [clojure.walk :as w]
                        [cljs.test :refer [is]]))
      (is (= {:a 1, :b {:c 2}} (w/stringify-keys {:a 1 :b {:c 2}})))))

(deftest-eval keywordize-keys-test
  (do (ns foo (:require [clojure.walk :as w]
                        [cljs.test :refer [is]]))
      (is (= {:a 1, :b {:c 2}} (w/keywordize-keys {:a 1 :b {:c 2}})))))

;; --- demo fns: should not throw and return the walked form unchanged ---

(deftest-eval demo-fns-test
  (do (ns foo (:require [clojure.walk :as w]
                        [cljs.test :refer [is]]))
      (is (= [[1 2] [1 2]] [(w/postwalk-demo [1 2]) (w/prewalk-demo [1 2])]))))

;; --- metadata preservation (clojure retain-meta / clojurescript preserves-meta) ---

(deftest-eval postwalk-preserves-meta-test
  (do (ns foo (:require [clojure.walk :as w]
                        [cljs.test :refer [is]]))
      (is (= {:foo 3} (-> (w/postwalk identity [1 (with-meta [1 2] {:foo 3})])
                          (nth 1)
                          meta)))))

(deftest-eval prewalk-preserves-meta-test
  (do (ns foo (:require [clojure.walk :as w]
                        [cljs.test :refer [is]]))
      (is (= {:foo 3} (-> (w/prewalk identity [1 (with-meta [1 2] {:foo 3})])
                          (nth 1)
                          meta)))))

(deftest-eval walk-retains-meta-on-root-test
  (do (ns foo (:require [clojure.walk :as w]
                        [cljs.test :refer [is]]))
      (is (let [m {:foo true}]
            (and (= m (meta (w/postwalk identity (with-meta (list 1 2) m))))
                 (= m (meta (w/postwalk identity (with-meta [1 2] m))))
                 (= m (meta (w/postwalk identity (with-meta #{1 2} m))))
                 (= m (meta (w/postwalk identity (with-meta {1 2} m)))))))))
