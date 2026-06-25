(ns squint.string-test
  (:require
   [clojure.test :as t :refer [deftest]]
   [squint.compiler :as compiler]
   [squint.test-utils :refer [eq]]
   #_:clj-kondo/ignore
   ["fs" :as fs]
   #_:clj-kondo/ignore
   ["path" :as path]
   ;; required at least by the `squint.eval-macro/evalll` macro
   ["url" :as url])
  (:require-macros [squint.eval-macro :refer [evalll]]))

(defn compile! [str-or-expr]
  (let [s (if (string? str-or-expr)
            str-or-expr
            (pr-str str-or-expr))]
    (compiler/compile-string s)))

(def dyn-import (js/eval "(x) => import(x)"))

(deftest ^:async blank?-test
  (evalll true
          '(do (ns foo (:require [squint.string :as str]))
               (def result (str/blank? "")))))

(deftest ^:async join-test
  (evalll "0--1--2--3--4--5--6--7--8--9"
          '(do (ns foo (:require [squint.string :as str]))
               (def result (str/join "--" (range 10))))))

(deftest ^:async replace-test
  (evalll "yyxxyyxx"
          '(do (ns foo (:require [squint.string :as str]))
               (def result (str/replace "--xx--xx" "--" "yy")))))

(deftest ^:async split-test
  (evalll (eq ["foo" "bar"])
          '(do (ns foo (:require [squint.string :as str]))
               (def result (str/split "foo\nbar\n\n" #"\n")))))

(deftest ^:async split-limit-test
  ;; positive limit caps the number of splits and keeps the remainder,
  ;; matching Clojure (not JS truncation)
  (evalll (eq ["a" "b-c-d"])
          '(do (ns foo (:require [squint.string :as str]))
               (def result (str/split "a-b-c-d" #"-" 2))))
  (evalll (eq ["a" "b" "c-d"])
          '(do (ns foo (:require [squint.string :as str]))
               (def result (str/split "a-b-c-d" #"-" 3))))
  ;; limit 0 / unset discards trailing empties; negative keeps them
  (evalll (eq ["a" "b"])
          '(do (ns foo (:require [squint.string :as str]))
               (def result (str/split "a-b-" #"-" 0))))
  (evalll (eq ["a" "b" ""])
          '(do (ns foo (:require [squint.string :as str]))
               (def result (str/split "a-b-" #"-" -1)))))

(deftest ^:async split-lines-test
  (evalll (eq ["foo" "bar"])
          '(do (ns foo (:require [squint.string :as str]))
               (def result (str/split-lines "foo\nbar\n\n")))))

(deftest ^:async lower-case-test
  (evalll "foobar"
          '(do (ns foo (:require [squint.string :as str]))
               (def result (str/lower-case "FooBar")))))

(deftest ^:async upper-case-test
  (evalll "FOOBAR"
          '(do (ns foo (:require [squint.string :as str]))
               (def result (str/upper-case "FooBar")))))

(deftest ^:async capitalize-test
  (evalll (eq ["" "F" "Foobar"])
          '(do (ns foo (:require [squint.string :as str]))
               (def result [(str/capitalize "")
                            (str/capitalize "f")
                            (str/capitalize "FooBar")]))))

(deftest ^:async includes-test
  (evalll (eq [true])
          '(do (ns foo (:require [squint.string :as str]))
               (def result [(str/includes? "foo" "o")]))))

;; (deftest ^:async string-conflict-test
;;   (evalll (fn [res]
;;             (eq ["foo","bar"] res))
;;           '(do (ns foo (:require [squint.string :as str]))
;;                (defn split [x] (str x)) (def result (str/split "foo,bar" ",")))))

;; (deftest ^:async split-test-string
;;   (evalll (fn [res]
;;             (eq ["foo","bar","baz"] res))
;;           '(do (ns foo (:require [squint.string :as str]))
;;                (def result (str/split "foo--bar--baz" "--")))))

;; (deftest ^:async split-test-regex
;;   (evalll (fn [res]
;;             (eq ["foo","bar","baz"] res))
;;           '(do (ns foo (:require [squint.string :as str]))
;;                (def result (str/split "fooxbarybaz" #"[xy]")))))
