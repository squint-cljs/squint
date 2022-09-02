(ns clava.string-test
  (:require
   ["fs" :as fs]
   ["path" :as path]
   [clava.compiler :as compiler]
   [clojure.test :as t :refer [async deftest is]])
  (:require-macros [clava.eval-macro :refer [evalll]]))

(defn compile! [str-or-expr]
  (let [s (if (string? str-or-expr)
            str-or-expr
            (pr-str str-or-expr))]
    (compiler/compile-string s)))

(def dyn-import (js/eval (js* "(x) => import(x)")))

(deftest blank?-test
  (evalll true
          '(do (ns foo (:require [clava.string :as str]))
               (def result (str/blank? "")))))

(deftest join-test
  (evalll "0--1--2--3--4--5--6--7--8--9"
          '(do (ns foo (:require [clava.string :as str]))
               (def result (str/join "--" (range 10))))))
