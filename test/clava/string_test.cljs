(ns clava.string-test
  (:require
   #_:clj-kondo/ignore
   ["fs" :as fs]
   #_:clj-kondo/ignore
   ["path" :as path]
   [clava.compiler :as compiler]
   [clava.test-utils :refer [eq]]
   [clojure.test :as t :refer [deftest]])
  (:require-macros [clava.eval-macro :refer [evalll]]))

(defn compile! [str-or-expr]
  (let [s (if (string? str-or-expr)
            str-or-expr
            (pr-str str-or-expr))]
    (compiler/compile-string s)))

(def dyn-import (js/eval "(x) => import(x)"))

(deftest blank?-test
  (evalll true
          '(do (ns foo (:require [clava.string :as str]))
               (def result (str/blank? "")))))

(deftest join-test
  (evalll "0--1--2--3--4--5--6--7--8--9"
          '(do (ns foo (:require [clava.string :as str]))
               (def result (str/join "--" (range 10))))))

(deftest string-conflict-test
  (evalll (fn [res]
            (eq ["foo","bar"] res))
          '(do (ns foo (:require [clava.string :as str]))
               (defn split [x] (str x)) (def result (str/split "foo,bar" ",")))))

(deftest split-test-string
  (evalll (fn [res]
            (eq ["foo","bar","baz"] res))
          '(do (ns foo (:require [clava.string :as str]))
               (def result (str/split "foo--bar--baz" "--")))))

(deftest split-test-regex
  (evalll (fn [res]
            (eq ["foo","bar","baz"] res))
          '(do (ns foo (:require [clava.string :as str]))
               (def result (str/split "fooxbarybaz" #"[xy]")))))
