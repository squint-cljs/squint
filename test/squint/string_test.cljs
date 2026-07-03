(ns squint.string-test
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

(deftest-eval blank?-test
  (do (ns foo (:require [squint.string :as str]
                        [cljs.test :refer [is]]))
      (is (str/blank? ""))))

(deftest-eval emoji-alias-test
  ;; an emoji ns alias must munge to a valid JS identifier for `import * as`
  (do (ns foo (:require [squint.string :as 😀]
                        [cljs.test :refer [is]]))
      (is (= "HI" (😀/upper-case "hi")))))

(deftest-eval join-test
  (do (ns foo (:require [squint.string :as str]
                        [cljs.test :refer [is]]))
      (is (= "0--1--2--3--4--5--6--7--8--9" (str/join "--" (range 10))))))

(deftest-eval replace-test
  (do (ns foo (:require [squint.string :as str]
                        [cljs.test :refer [is]]))
      (is (= "yyxxyyxx" (str/replace "--xx--xx" "--" "yy")))))

(deftest-eval replace-first-test
  (do (ns foo (:require [squint.string :as str]
                        [cljs.test :refer [is]]))
      (is (= "yyxx--xx" (str/replace-first "--xx--xx" "--" "yy")))
      (is (= "yyxx--xx" (str/replace-first "--xx--xx" #"--" "yy")))
      (is (= "a[b]c" (str/replace-first "abc" #"(b)" "[$1]")))
      (is (= "a$xc" (str/replace-first "abc" "b" "$x")))
      (is (= "abc" (str/replace-first "abc" #"x" "y")))))

(deftest-eval split-test
  (do (ns foo (:require [squint.string :as str]
                        [cljs.test :refer [is]]))
      (is (= ["foo" "bar"] (str/split "foo\nbar\n\n" #"\n")))))

(deftest-eval split-limit-test
  (do (ns foo (:require [squint.string :as str]
                        [cljs.test :refer [is]]))
      ;; positive limit caps the number of splits and keeps the remainder,
      ;; matching Clojure (not JS truncation)
      (is (= ["a" "b-c-d"] (str/split "a-b-c-d" #"-" 2)))
      (is (= ["a" "b" "c-d"] (str/split "a-b-c-d" #"-" 3)))
      ;; limit 0 / unset discards trailing empties; negative keeps them
      (is (= ["a" "b"] (str/split "a-b-" #"-" 0)))
      (is (= ["a" "b" ""] (str/split "a-b-" #"-" -1)))))

(deftest-eval split-lines-test
  (do (ns foo (:require [squint.string :as str]
                        [cljs.test :refer [is]]))
      (is (= ["foo" "bar"] (str/split-lines "foo\nbar\n\n")))))

(deftest-eval lower-case-test
  (do (ns foo (:require [squint.string :as str]
                        [cljs.test :refer [is]]))
      (is (= "foobar" (str/lower-case "FooBar")))))

(deftest-eval upper-case-test
  (do (ns foo (:require [squint.string :as str]
                        [cljs.test :refer [is]]))
      (is (= "FOOBAR" (str/upper-case "FooBar")))))

(deftest-eval capitalize-test
  (do (ns foo (:require [squint.string :as str]
                        [cljs.test :refer [is]]))
      (is (= ["" "F" "Foobar"]
             [(str/capitalize "")
              (str/capitalize "f")
              (str/capitalize "FooBar")]))))

(deftest-eval includes-test
  (do (ns foo (:require [squint.string :as str]
                        [cljs.test :refer [is]]))
      (is (str/includes? "foo" "o"))))

(deftest-eval reverse-test
  (do (ns foo (:require [squint.string :as str]
                        [cljs.test :refer [is]]))
      (is (= ["" "cba" "a֎"]
             [(str/reverse "")
              (str/reverse "abc")
              (str/reverse "֎a")]))))

(deftest-eval escape-test
  (do (ns foo (:require [squint.string :as str]
                        [cljs.test :refer [is]]))
      (is (= ["" "A_AbC_C" "xbc"]
             [(str/escape "" {})
              (str/escape "abc" {"a" "A_A" "c" "C_C"})
              (str/escape "abc" {"a" "x"})]))))

(deftest-eval blank?-non-string-test
  (do (ns foo (:require [squint.string :as str]
                        [cljs.test :refer [is]]))
      (is (= [false true true false]
             [(str/blank? 123)
              (str/blank? nil)
              (str/blank? "  ")
              (str/blank? "x")]))))
