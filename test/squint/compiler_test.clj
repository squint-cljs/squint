(ns squint.compiler-test
  (:require
   [babashka.fs :as fs]
   [babashka.process :refer [sh] :as p]
   [clojure.string :as str]
   [clojure.test :refer [deftest is] :as t]
   [clojure.edn :as edn]
   [squint.compiler :as sq]
   [squint.compiler.lift-iife :as lift-iife]
   [squint.compiler.source-map :as sm]))

(defn to-js [code {:keys [requires]}]
  (sq/compile-string
   (str "(ns module"
        "(:require " (str/join "\n" requires) "))"
        code)))

(defn test-expr [code]
  (let [js (to-js code [])
        tmp-dir (fs/file ".test")
        _ (fs/create-dirs tmp-dir)
        tmp-file (fs/file tmp-dir "expr.js")
        _ (spit tmp-file js)
        {:keys [out]}  (p/check (sh ["node" (str tmp-file)]))]
    (str/trim out)))

(deftest compiler-test
  (is (str/includes? (test-expr "(prn (+ 1 2 3))")
                     "6"))
  (is (str/includes? (test-expr "(ns foo (:require [\"fs\" :as fs])) (prn (fs/existsSync \".\"))")
                     "true"))
  (is (= [0 1 2 3 4 5 6 7 8 9]
         (edn/read-string
          (format "[%s]" (test-expr "(vec (for [i (range 10)] (println i)))"))))))

(deftest jvm-host-macro-test
  ;; syntax-quoted core vars in macro expansions must resolve to the core
  ;; module on the JVM host, not to the macro's own namespace
  (let [js (to-js "(time (prn :timed))
                   (doseq [x [1 2 3]] (prn x))
                   (defn f ([x] x) ([x & ys] ys))
                   (defprotocol P (pfoo [x]))
                   (deftype T [] P (pfoo [_] :pfoo))
                   (with-out-str (print :out))"
                  {})]
    (is (not (str/includes? js ".internal."))))
  (is (str/includes? (test-expr "(time (prn :timed))") "Elapsed time"))
  (is (str/includes? (test-expr "(doseq [x [1 2 3]] (prn x))") "3"))
  (is (str/includes? (test-expr "(defn f ([x] x) ([x & ys] ys)) (prn (f 1 2 3))") "[2 3]"))
  (is (str/includes? (test-expr "(defprotocol P (pfoo [x]))
                                 (deftype T [] P (pfoo [_] :pfoo))
                                 (prn (pfoo (->T)))")
                     "pfoo"))
  (is (str/includes? (test-expr "(prn (with-out-str (print :out)))") "out")))

(deftest js-reserved-word-test
  ;; clojure.core/munge leaves JS reserved words alone, cljs.core/munge appends $
  (is (str/includes? (sq/compile-string "(defn f [new] new)") "new$"))
  (is (str/includes?
       (sq/compile-string "(ns foo (:require [squint.core :refer [defclass]]))
                           (defclass MyElement (extends js/HTMLElement) (constructor [this] (super)))")
       "const this$ = this;"))
  (is (= "1" (test-expr "(prn ((fn [this] this) 1))")))
  (is (str/includes? (sq/compile-string "(def eval 1)") "var eval$ = 1"))
  (is (str/includes? (sq/compile-string "(js/eval \"1\")") "eval(\"1\")")))

(deftest regex-literal-test
  (is (str/includes? (sq/compile-string "#\"(?i)x/y\"") "/x\\/y/i"))
  (is (str/includes? (sq/compile-string "#\"\"") "(new RegExp(\"\"))"))
  (is (thrown? Exception (sq/compile-string "#\"[{}[]\\\"]\"")))
  (is (= "true" (test-expr "(prn (some? (re-find #\"(?i)a/b\" \"A/B\")))")))
  (t/testing "a regex literal in return position is returned"
    (is (str/includes? (sq/compile-string "(defn f [] #\"a\")") "return /a/"))
    (is (= "true" (test-expr "(prn (some? ((fn [] #\"a\"))))")))))

(defn- gen-pos [js s]
  (let [i (str/index-of js s)
        before (subs js 0 i)]
    [(count (filter #{\newline} before))
     (- i (inc (or (str/last-index-of before "\n") -1)))]))

(deftest source-map-test
  (let [src "(ns m)\n(defn f [x]\n  (g\n   (h x)))"
        {:keys [javascript source-map-json source-map-segments]}
        (sq/compile* src {:source-map {:file "m.mjs" :source "m.cljs"}})
        mapped (set (map (fn [[gl gc sl sc]] [[gl gc] [sl sc]]) source-map-segments))]
    (t/testing "a call maps to the line and column of its form"
      (is (contains? mapped [(gen-pos javascript "g(h(x))") [2 2]]))
      (is (contains? mapped [(gen-pos javascript "h(x)") [3 3]])))
    (t/testing "the map names the source and embeds its content"
      (is (str/includes? source-map-json "\"sources\":[\"m.cljs\"]"))
      (is (str/includes? source-map-json "\"sourcesContent\":[\"(ns m)\\n(defn f")))
    (t/testing "a pragma before the imports shifts the mappings"
      (let [{:keys [javascript source-map-segments]}
            (sq/compile* (str "\"use client\"\n" src) {:source-map true})
            mapped (set (map (fn [[gl gc sl sc]] [[gl gc] [sl sc]]) source-map-segments))]
        (is (str/starts-with? javascript "\"use client\"\n"))
        (is (contains? mapped [(gen-pos javascript "g(h(x))") [3 2]]))))
    (t/testing "compiling without :source-map returns no map"
      (is (nil? (:source-map-json (sq/compile* src)))))))

(defn- lifted [src]
  (:javascript (sq/compile* src {:passes [lift-iife/lift] :elide-imports true :elide-exports true})))

(deftest source-map-encode-test
  (t/testing "a segment with source index 1 maps to the second source"
    (is (= (str "{\"version\":3,\"file\":\"b.js\",\"sources\":[\"a.clj\",\"b.clj\"],"
                "\"sourcesContent\":[null,\"(x)\"],\"names\":[],\"mappings\":\"AAAA;ACAA,IAEG\"}")
           (sm/encode [[0 0 0 0] [1 0 0 0 1] [1 4 2 3 1]]
                      {:file "b.js" :sources ["a.clj" "b.clj"] :sources-content [nil "(x)"]}))))
  (t/testing "shift-segments keeps the source index"
    (is (= [[2 1 2 3 4] [3 0 0 0]] (sm/shift-segments [[0 1 2 3 4] [1 0 0 0]] "x\ny\n")))))

(defn- mapped-at
  "Returns the 1-based [line column] of the source mapped at generated position
  pos, or nil if no segment covers it."
  [segments [gl gc]]
  (some->> segments
           (filter (fn [[l c]] (and (= l gl) (<= c gc))))
           last
           (drop 2)
           (mapv inc)))

(deftest source-map-locations-test
  (doseq [[desc src generated expected opts]
          [["a call in a let binding maps to its form" "(defn f [] (let [a (g 1)] a))" "g(1)" [1 20]]
           ["a call in an if test maps to its form" "(defn f [x] (if (g x) 1 2))" "g(x)" [1 17]]
           ["a call in a nested fn maps to its form" "(defn f [] (fn [] (h 2)))" "h(2)" [1 19]]
           ["a constructor call maps to its form" "(defn f [] (js/Date. 1))" "new Date(1)" [1 12]]
           ["a method call maps to its form" "(defn f [o] (.foo o 1))" "o.foo(1)" [1 13]]
           ["@a maps to the @" "(defn f [a] @a)" "squint_core.deref(a)" [1 13]]
           ["a call in #() maps to its form" "(defn f [] (map #(g %) [1]))" "g(_PERCENT" [1 18]]
           ["text after a nested call maps to the enclosing form" "(defn f [x] (g (h x) 2))" ", 2)" [1 13]]
           ["a call in a vector literal has no mapping" "(defn f [] [(g 1)])" "g(1)" nil]
           ["a call lifted out of an IIFE keeps its form"
            "(defn f [a]\n  (let [b (let [c (g a)]\n            (h c))]\n    (k b)))"
            "h(c_2)" [3 13] {:passes [lift-iife/lift]}]]]
    (let [{:keys [javascript source-map-segments]}
          (sq/compile* src (merge {:source-map true :elide-imports true :elide-exports true} opts))]
      (t/testing desc
        (is (= expected (mapped-at source-map-segments (gen-pos javascript generated))))))))

(deftest lift-iife-test
  (t/testing "a let in a binding init becomes statements"
    (is (= "var f = function (a) {\nconst c_2 = g(a);\nconst b_1 = h(c_2);\nreturn k(b_1);\n\n};\n"
           (lifted "(defn f [a] (let [b (let [c (g a)] (h c))] (k b)))"))))
  (t/testing "an or in an if test assigns a temporary in each branch"
    (is (= (str "var f = function (a) {\nconst or_1_2 = g(a);\nlet squint$iife$1;\n"
                "if (squint_core.truth_(or_1_2)) {\nsquint$iife$1 = or_1_2} else {\nsquint$iife$1 = h(a)};\n"
                "if (squint_core.truth_(squint$iife$1)) {\nreturn 1} else {\nreturn 2};\n\n};\n")
           (lifted "(defn f [a] (if (or (g a) (h a)) 1 2))"))))
  (t/testing "an IIFE after a call argument stays"
    (is (str/includes? (lifted "(defn f [a] (k (g a) (let [z (g a)] (h z))))") "(() => {")))
  (t/testing "an IIFE at module level stays"
    (is (str/includes? (lifted "(def x (let [a (f 1)] (g a)))") "(() => {")))
  (t/testing "a function lifted into callee position gets parens"
    (is (= "var f = function () {\nconst a_1 = g(1);\n(function (y) {\nreturn (a_1 + y);\n\n})(g(2));\nreturn null;\n\n};\n"
           (lifted "(defn f [] ((let [a (g 1)] (fn [y] (+ a y))) (g 2)) nil)")))
    (is (= "var f = function () {\nconst a_1 = g(1);\nreturn ((y)=>(a_1 + y))(g(2));\n\n};\n"
           (lifted "(defn f [] ((let [a (g 1)] (fn ^:=> [y] (+ a y))) (g 2)))"))))
  (t/testing "a call lifted into a new callee gets parens"
    (is (= "var f = function () {\nconst a_1 = g(1);\nreturn (new (h(a_1))(1));\n\n};\n"
           (lifted "(defn f [] (new (let [a (g 1)] (h a)) 1))"))))
  (t/testing "an async IIFE stays"
    (is (str/includes? (lifted "(defn ^:async f [] (k (let [z (await (g))] (h z))))") "(async () => {")))
  (t/testing "without :passes the output keeps the IIFE"
    (is (str/includes? (sq/compile-string "(defn f [a] (let [b (let [c (g a)] (h c))] (k b)))")
                       "(() => {"))))

(def our-ns *ns*)
(defn run-tests [_]
  (let [{:keys [fail error]}
        (t/run-tests our-ns)]
    (when (pos? (+ fail error))
      (throw (ex-info "Tests failed" {:babashka/exit 1})))))

(comment
  (test-expr "(prn (+ 1 2 3))")
  )
