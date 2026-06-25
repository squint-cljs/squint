(ns squint.edn-test
  (:require
   [clojure.test :as t :refer [deftest]]
   [squint.compiler :as compiler]
   #_:clj-kondo/ignore
   ["fs" :as fs]
   #_:clj-kondo/ignore
   ["path" :as path]
   ;; required by the `squint.eval-macro/evalll` macro
   #_:clj-kondo/ignore
   ["url" :as url])
  (:require-macros [squint.eval-macro :refer [evalll]]))

(defn compile! [str-or-expr]
  (let [s (if (string? str-or-expr)
            str-or-expr
            (pr-str str-or-expr))]
    (compiler/compile-string s)))

(def dyn-import (js/eval "(x) => import(x)"))

(deftest scalars-test
  (evalll true
          '(do (ns foo (:require [clojure.edn :as edn]))
               (def result
                 (and (= 1 (edn/read-string "1"))
                      (= -1 (edn/read-string "-1"))
                      (= -1.5 (edn/read-string "-1.5"))
                      (= nil (edn/read-string "nil"))
                      (= true (edn/read-string "true"))
                      (= false (edn/read-string "false"))
                      (= "foo" (edn/read-string "\"foo\""))
                      (= :hello (edn/read-string ":hello"))
                      (= :foo/bar (edn/read-string ":foo/bar"))
                      (= "goodbye" (edn/read-string "goodbye")))))))

(deftest number-test
  (evalll true
          '(do (ns foo (:require [clojure.edn :as edn]))
               (def result
                 (and (apply = 0 (map edn/read-string ["0" "+0" "-0"]))
                      (apply = 42 (map edn/read-string ["052" "0x2a" "2r101010" "8R52" "16r2a" "36r16"]))
                      (apply = -42 (map edn/read-string ["-052" "-0X2a" "-2r101010" "-8r52" "-16r2a" "-36R16"]))
                      (= 1000 (edn/read-string "1e3"))
                      (= 1.5 (edn/read-string "1.5M"))
                      (= 1.0 (edn/read-string "1."))
                      (= 174 (edn/read-string "0xAE"))
                      ;; N suffix is ignored, like CLJS: plain number
                      (= 42 (edn/read-string "42N"))
                      (= 42 (edn/read-string "0x2aN"))
                      (= 42 (edn/read-string "052N")))))))

(deftest collections-test
  (evalll true
          '(do (ns foo (:require [clojure.edn :as edn]))
               (def result
                 (and (= [3 4] (edn/read-string "[3 4]"))
                      (= (list 7 8 9) (edn/read-string "(7 8 9)"))
                      (= #{1 2 3} (edn/read-string "#{1 2 3}"))
                      (= {:a 1 :b 2} (edn/read-string "{:a 1 :b 2}"))
                      (= {:a 1 :b 2} (edn/read-string "{:a 1, :b 2}"))
                      (= [:a "b" #{"c" {:d [:e :f :g]}}]
                         (edn/read-string "[:a b #{c {:d [:e :f :g]}}]")))))))

(deftest string-escape-test
  (evalll true
          '(do (ns foo (:require [clojure.edn :as edn]))
               (def result
                 (= "tab\tnewline\nquote\"slash\\back\b"
                    (edn/read-string "\"tab\\tnewline\\nquote\\\"slash\\\\back\\b\""))))))

(deftest char-test
  (evalll true
          '(do (ns foo (:require [clojure.edn :as edn]))
               (def result
                 (and (= "a" (edn/read-string "\\a"))
                      (= "\n" (edn/read-string "\\newline"))
                      (= " " (edn/read-string "\\space"))
                      (= "\t" (edn/read-string "\\tab")))))))

(deftest discard-comment-test
  (evalll true
          '(do (ns foo (:require [clojure.edn :as edn]))
               (def result
                 (and (= 2 (edn/read-string "#_nope 2"))
                      (= [1 3] (edn/read-string "[1 #_2 3]"))
                      (= 3 (edn/read-string ";comment\n3")))))))

(deftest inst-test
  (evalll true
          '(do (ns foo (:require [clojure.edn :as edn]))
               (def result
                 (= (.valueOf (js/Date. "2010-11-12T13:14:15.666-05:00"))
                    (.valueOf (edn/read-string "#inst \"2010-11-12T13:14:15.666-05:00\"")))))))

(deftest uuid-test
  (evalll true
          '(do (ns foo (:require [clojure.edn :as edn]))
               (def result
                 (= "550e8400-e29b-41d4-a716-446655440000"
                    (edn/read-string "#uuid \"550e8400-e29b-41d4-a716-446655440000\""))))))

(deftest custom-reader-test
  (evalll true
          '(do (ns foo (:require [clojure.edn :as edn]))
               (def result
                 (and (= "WRAPPED:5"
                         (edn/read-string {:readers {'foo (fn [v] (str "WRAPPED:" v))}}
                                          "#foo 5"))
                      (= ["bar" 5]
                         (edn/read-string {:default (fn [tag v] [tag v])}
                                          "#bar 5")))))))

(deftest eof-test
  (evalll true
          '(do (ns foo (:require [clojure.edn :as edn]))
               (def result
                 (and (= nil (edn/read-string ""))
                      (= :end (edn/read-string {:eof :end} "")))))))

;; edge cases adapted from edamame.core-test

(deftest keyword-edge-test
  ;; :nil reads as a keyword, not nil; keywords stay strings in squint
  (evalll true
          '(do (ns foo (:require [clojure.edn :as edn]))
               (def result
                 (and (= :nil (edn/read-string ":nil"))
                      (= :123 (edn/read-string ":123"))
                      (= :false (edn/read-string ":false"))
                      (= :5K (edn/read-string ":5K")))))))

(deftest shebang-test
  (evalll true
          '(do (ns foo (:require [clojure.edn :as edn]))
               (def result
                 (= {:a 1} (edn/read-string "#!/usr/bin/env bb\n{:a 1}"))))))

(deftest trailing-discard-test
  (evalll true
          '(do (ns foo (:require [clojure.edn :as edn]))
               (def result
                 (and (= (list 1 2 3) (edn/read-string "(1 2 3 #_4)"))
                      (= 1 (edn/read-string "#_(+ 1 2 3) 1"))
                      ;; stacked discards drop two forms
                      (= [3] (edn/read-string "[#_ #_ 1 2 3]"))
                      (= [1 4] (edn/read-string "[1 #_ #_ 2 3 4]")))))))

(deftest blank-comments-test
  (evalll true
          '(do (ns foo (:require [clojure.edn :as edn]
                                 [clojure.string :as str]))
               (def result
                 (= nil (edn/read-string (str/join "\n" (repeat 100 ";;"))))))))

(deftest reader-macro-test
  (evalll true
          '(do (ns foo (:require [clojure.edn :as edn]))
               (def result
                 (and (= ##Inf (edn/read-string "##Inf"))
                      (= ##-Inf (edn/read-string "##-Inf"))
                      (js/Number.isNaN (edn/read-string "##NaN"))
                      ;; quote is supported by clojure.edn
                      (= '(quote foo) (edn/read-string "'foo"))
                      (= '(quote (1 2)) (edn/read-string "'(1 2)")))))))

(deftest metadata-test
  (evalll true
          '(do (ns foo (:require [clojure.edn :as edn]))
               (defn threw [s] (try (do (edn/read-string s) false) (catch :default _ true)))
               (def result
                 (and (= [1] (edn/read-string "^:a [1]"))
                      ;; keyword meta -> {kw true}
                      (= {:a true} (meta (edn/read-string "^:a [1]")))
                      ;; symbol or string meta -> {:tag ...}
                      (= {:tag "foo"} (meta (edn/read-string "^foo [1]")))
                      (= {:tag "s"} (meta (edn/read-string "^\"s\" [1]")))
                      ;; map meta as-is
                      (= {:a 1} (meta (edn/read-string "^{:a 1} [1]")))
                      ;; stacked metadata merges, outer wins
                      (= {:a true :b true} (meta (edn/read-string "^:a ^:b [1]")))
                      (= {:tag "foo"} (meta (edn/read-string "^foo ^bar [1]")))
                      ;; metadata only applies to collections
                      (threw "^:a 1")
                      (threw "^:a :kw"))))))

(deftest namespaced-map-test
  (evalll true
          '(do (ns foo (:require [clojure.edn :as edn]))
               (def result
                 (and (= {:foo/a 1 :foo/b 2} (edn/read-string "#:foo{:a 1 :b 2}"))
                      ;; existing namespace is kept
                      (= {:bar/x 1} (edn/read-string "#:foo{:bar/x 1}"))
                      ;; _ namespace removes the qualifier
                      (= {:a 1} (edn/read-string "#:foo{:_/a 1}"))
                      ;; whitespace between prefix and map
                      (= {:foo/a 1} (edn/read-string "#:foo {:a 1}")))))))

(deftest throwing-test
  (evalll true
          '(do (ns foo (:require [clojure.edn :as edn]))
               (defn threw [s]
                 (try (do (edn/read-string s) false)
                      (catch :default _ true)))
               (def result
                 (and (threw "#{1 1}")
                      ;; duplicate by value, independent of map key order
                      (threw "#{{:a 1 :b 2} {:b 2 :a 1}}")
                      (threw "{:a :b :a :c}")
                      (threw "{[1] :x [1] :y}")
                      (threw "{:a :b :c}")
                      (threw "  ]   ")
                      (threw "[1 2")
                      (threw "1a2")
                      ;; leading-zero non-octal is an invalid number
                      (threw "08")
                      ;; radix digit out of range, and N not allowed with radix
                      (threw "16rGG")
                      (threw "2r101N")
                      ;; empty keyword
                      (threw ":)")
                      (threw ": x")
                      ;; auto-resolved keywords and reader macros not in edn
                      (threw "::foo")
                      (threw "@foo")
                      (threw "~foo")
                      (threw "`foo"))))))
