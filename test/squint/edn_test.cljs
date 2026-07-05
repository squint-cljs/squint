(ns squint.edn-test
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

(deftest-eval scalars-test
  (do (ns foo (:require [clojure.edn :as edn]
                        [cljs.test :refer [is]]))
      (is (= 1 (edn/read-string "1")))
      (is (= -1 (edn/read-string "-1")))
      (is (= -1.5 (edn/read-string "-1.5")))
      (is (nil? (edn/read-string "nil")))
      (is (true? (edn/read-string "true")))
      (is (false? (edn/read-string "false")))
      (is (= "foo" (edn/read-string "\"foo\"")))
      (is (= :hello (edn/read-string ":hello")))
      (is (= :foo/bar (edn/read-string ":foo/bar")))
      (is (= "goodbye" (edn/read-string "goodbye")))))

(deftest-eval number-test
  (do (ns foo (:require [clojure.edn :as edn]
                        [cljs.test :refer [is]]))
      (is (apply = 0 (map edn/read-string ["0" "+0" "-0"])))
      (is (apply = 42 (map edn/read-string ["052" "0x2a" "2r101010" "8R52" "16r2a" "36r16"])))
      (is (apply = -42 (map edn/read-string ["-052" "-0X2a" "-2r101010" "-8r52" "-16r2a" "-36R16"])))
      (is (= 1000 (edn/read-string "1e3")))
      (is (= 1.5 (edn/read-string "1.5M")))
      (is (= 1.0 (edn/read-string "1.")))
      (is (= 174 (edn/read-string "0xAE")))
      ;; N suffix is ignored, like CLJS: plain number
      (is (= 42 (edn/read-string "42N")))
      (is (= 42 (edn/read-string "0x2aN")))
      (is (= 42 (edn/read-string "052N")))))

(deftest-eval collections-test
  (do (ns foo (:require [clojure.edn :as edn]
                        [cljs.test :refer [is]]))
      (is (= [3 4] (edn/read-string "[3 4]")))
      (is (= (list 7 8 9) (edn/read-string "(7 8 9)")))
      (is (= #{1 2 3} (edn/read-string "#{1 2 3}")))
      (is (= {:a 1 :b 2} (edn/read-string "{:a 1 :b 2}")))
      (is (= {:a 1 :b 2} (edn/read-string "{:a 1, :b 2}")))
      (is (= [:a "b" #{"c" {:d [:e :f :g]}}]
             (edn/read-string "[:a b #{c {:d [:e :f :g]}}]")))))

(deftest-eval string-escape-test
  (do (ns foo (:require [clojure.edn :as edn]
                        [cljs.test :refer [is]]))
      (is (= "tab\tnewline\nquote\"slash\\back\b"
             (edn/read-string "\"tab\\tnewline\\nquote\\\"slash\\\\back\\b\"")))))

(deftest-eval char-test
  (do (ns foo (:require [clojure.edn :as edn]
                        [cljs.test :refer [is]]))
      (is (= "a" (edn/read-string "\\a")))
      (is (= "\n" (edn/read-string "\\newline")))
      (is (= " " (edn/read-string "\\space")))
      (is (= "\t" (edn/read-string "\\tab")))))

(deftest-eval discard-comment-test
  (do (ns foo (:require [clojure.edn :as edn]
                        [cljs.test :refer [is]]))
      (is (= 2 (edn/read-string "#_nope 2")))
      (is (= [1 3] (edn/read-string "[1 #_2 3]")))
      (is (= 3 (edn/read-string ";comment\n3")))))

(deftest-eval inst-test
  (do (ns foo (:require [clojure.edn :as edn]
                        [cljs.test :refer [is]]))
      (is (= (.valueOf (js/Date. "2010-11-12T13:14:15.666-05:00"))
             (.valueOf (edn/read-string "#inst \"2010-11-12T13:14:15.666-05:00\""))))))

(deftest-eval uuid-test
  (do (ns foo (:require [clojure.edn :as edn]
                        [cljs.test :refer [is]]))
      (is (= "550e8400-e29b-41d4-a716-446655440000"
             (edn/read-string "#uuid \"550e8400-e29b-41d4-a716-446655440000\"")))))

(deftest-eval custom-reader-test
  (do (ns foo (:require [clojure.edn :as edn]
                        [cljs.test :refer [is]]))
      (is (= "WRAPPED:5"
             (edn/read-string {:readers {'foo (fn [v] (str "WRAPPED:" v))}}
                              "#foo 5")))
      (is (= ["bar" 5]
             (edn/read-string {:default (fn [tag v] [tag v])}
                              "#bar 5")))))

(deftest-eval eof-test
  (do (ns foo (:require [clojure.edn :as edn]
                        [cljs.test :refer [is]]))
      (is (nil? (edn/read-string "")))
      (is (= :end (edn/read-string {:eof :end} "")))))

;; edge cases adapted from edamame.core-test

(deftest-eval keyword-edge-test
  ;; :nil reads as a keyword, not nil; keywords stay strings in squint
  (do (ns foo (:require [clojure.edn :as edn]
                        [cljs.test :refer [is]]))
      (is (= :nil (edn/read-string ":nil")))
      (is (= :123 (edn/read-string ":123")))
      (is (= :false (edn/read-string ":false")))
      (is (= :5K (edn/read-string ":5K")))))

(deftest-eval shebang-test
  (do (ns foo (:require [clojure.edn :as edn]
                        [cljs.test :refer [is]]))
      (is (= {:a 1} (edn/read-string "#!/usr/bin/env bb\n{:a 1}")))))

(deftest-eval trailing-discard-test
  (do (ns foo (:require [clojure.edn :as edn]
                        [cljs.test :refer [is]]))
      (is (= (list 1 2 3) (edn/read-string "(1 2 3 #_4)")))
      (is (= 1 (edn/read-string "#_(+ 1 2 3) 1")))
      ;; stacked discards drop two forms
      (is (= [3] (edn/read-string "[#_ #_ 1 2 3]")))
      (is (= [1 4] (edn/read-string "[1 #_ #_ 2 3 4]")))))

(deftest-eval blank-comments-test
  (do (ns foo (:require [clojure.edn :as edn]
                        [clojure.string :as str]
                        [cljs.test :refer [is]]))
      (is (nil? (edn/read-string (str/join "\n" (repeat 100 ";;")))))))

(deftest-eval reader-macro-test
  (do (ns foo (:require [clojure.edn :as edn]
                        [cljs.test :refer [is]]))
      (is (= ##Inf (edn/read-string "##Inf")))
      (is (= ##-Inf (edn/read-string "##-Inf")))
      (is (js/Number.isNaN (edn/read-string "##NaN")))
      ;; quote is supported by clojure.edn
      (is (= '(quote foo) (edn/read-string "'foo")))
      (is (= '(quote (1 2)) (edn/read-string "'(1 2)")))))

(deftest-eval metadata-test
  (do (ns foo (:require [clojure.edn :as edn]
                        [cljs.test :refer [is]]))
      (defn threw [s] (try (edn/read-string s) false (catch :default _ true)))
      (is (= [1] (edn/read-string "^:a [1]")))
      ;; keyword meta -> {kw true}
      (is (= {:a true} (meta (edn/read-string "^:a [1]"))))
      ;; symbol or string meta -> {:tag ...}
      (is (= {:tag "foo"} (meta (edn/read-string "^foo [1]"))))
      (is (= {:tag "s"} (meta (edn/read-string "^\"s\" [1]"))))
      ;; map meta as-is
      (is (= {:a 1} (meta (edn/read-string "^{:a 1} [1]"))))
      ;; stacked metadata merges, outer wins
      (is (= {:a true :b true} (meta (edn/read-string "^:a ^:b [1]"))))
      (is (= {:tag "foo"} (meta (edn/read-string "^foo ^bar [1]"))))
      ;; metadata only applies to collections
      (is (threw "^:a 1"))
      (is (threw "^:a :kw"))))

(deftest-eval namespaced-map-test
  (do (ns foo (:require [clojure.edn :as edn]
                        [cljs.test :refer [is]]))
      (is (= {:foo/a 1 :foo/b 2} (edn/read-string "#:foo{:a 1 :b 2}")))
      ;; existing namespace is kept
      (is (= {:bar/x 1} (edn/read-string "#:foo{:bar/x 1}")))
      ;; _ namespace removes the qualifier
      (is (= {:a 1} (edn/read-string "#:foo{:_/a 1}")))
      ;; whitespace between prefix and map
      (is (= {:foo/a 1} (edn/read-string "#:foo {:a 1}")))))

(deftest-eval throwing-test
  (do (ns foo (:require [clojure.edn :as edn]
                        [cljs.test :refer [is]]))
      (defn threw [s]
        (try (edn/read-string s) false
             (catch :default _ true)))
      (is (threw "#{1 1}"))
      ;; duplicate by value, independent of map key order
      (is (threw "#{{:a 1 :b 2} {:b 2 :a 1}}"))
      (is (threw "{:a :b :a :c}"))
      (is (threw "{[1] :x [1] :y}"))
      (is (threw "{:a :b :c}"))
      (is (threw "  ]   "))
      (is (threw "[1 2"))
      (is (threw "1a2"))
      ;; leading-zero non-octal is an invalid number
      (is (threw "08"))
      ;; radix digit out of range, and N not allowed with radix
      (is (threw "16rGG"))
      (is (threw "2r101N"))
      ;; empty keyword
      (is (threw ":)"))
      (is (threw ": x"))
      ;; auto-resolved keywords and reader macros not in edn
      (is (threw "::foo"))
      (is (threw "@foo"))
      (is (threw "~foo"))
      (is (threw "`foo"))))
