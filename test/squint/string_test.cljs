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

(deftest blank?-test
  (evalll true
          '(do (ns foo (:require [squint.string :as str]))
               (def result (str/blank? "")))))

(deftest join-test
  (evalll "0--1--2--3--4--5--6--7--8--9"
          '(do (ns foo (:require [squint.string :as str]))
               (def result (str/join "--" (range 10))))))

(deftest replace-test
  (evalll "yyxxyyxx"
          '(do (ns foo (:require [squint.string :as str]))
               (def result (str/replace "--xx--xx" "--" "yy")))))

(deftest split-test
  (evalll (eq ["foo" "bar"])
          '(do (ns foo (:require [squint.string :as str]))
               (def result (str/split "foo\nbar\n\n" #"\n")))))

(deftest split-lines-test
  (evalll (eq ["foo" "bar"])
          '(do (ns foo (:require [squint.string :as str]))
               (def result (str/split-lines "foo\nbar\n\n")))))

(deftest lower-case-test
  (evalll "foobar"
          '(do (ns foo (:require [squint.string :as str]))
               (def result (str/lower-case "FooBar")))))

(deftest upper-case-test
  (evalll "FOOBAR"
          '(do (ns foo (:require [squint.string :as str]))
               (def result (str/upper-case "FooBar")))))

(deftest capitalize-test
  (evalll (eq ["" "F" "Foobar"])
          '(do (ns foo (:require [squint.string :as str]))
               (def result [(str/capitalize "")
                            (str/capitalize "f")
                            (str/capitalize "FooBar")]))))

(deftest includes-test
  (evalll (eq [true])
          '(do (ns foo (:require [squint.string :as str]))
               (def result [(str/includes? "foo" "o")]))))

;; (deftest string-conflict-test
;;   (evalll (fn [res]
;;             (eq ["foo","bar"] res))
;;           '(do (ns foo (:require [squint.string :as str]))
;;                (defn split [x] (str x)) (def result (str/split "foo,bar" ",")))))

;; (deftest split-test-string
;;   (evalll (fn [res]
;;             (eq ["foo","bar","baz"] res))
;;           '(do (ns foo (:require [squint.string :as str]))
;;                (def result (str/split "foo--bar--baz" "--")))))

;; (deftest split-test-regex
;;   (evalll (fn [res]
;;             (eq ["foo","bar","baz"] res))
;;           '(do (ns foo (:require [squint.string :as str]))
;;                (def result (str/split "fooxbarybaz" #"[xy]")))))
