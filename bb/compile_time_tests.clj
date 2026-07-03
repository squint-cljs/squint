(ns compile-time-tests
  "Isolation checks for {:squint/compile-time true} extraction. The functional
  surface (aliasing, refer, self-use) is exercised for real by test-project;
  here we assert what running code cannot: that the runtime namespace is never
  evaluated in SCI when its compile-time part is loaded."
  (:require
   [babashka.fs :as fs]
   [babashka.process :as p]
   [clojure.test :as t :refer [deftest is]]
   [clojure.string :as str]))

(def test-dir ".work/compile-time-test")

(t/use-fixtures :each (fn [f]
                        (when (fs/exists? test-dir)
                          (fs/delete-tree test-dir))
                        (fs/create-dirs test-dir)
                        (f)))

(defn squint [& args]
  (apply p/shell {:continue true :dir test-dir :out :string :err :string}
         (into ["node" "../../node_cli.js"] args)))

(deftest runtime-ns-not-evaluated-test
  ;; The runtime part carries a top-level throw. Loading the compile-time part
  ;; alone leaves it untouched, so a clean compile proves squint did not
  ;; evaluate the whole runtime namespace in SCI.
  (let [src (fs/file test-dir "src" "t")]
    (fs/create-dirs src)
    (spit (fs/file test-dir "squint.edn")
          (pr-str {:paths ["src"] :output-dir "out" :extension "mjs"}))
    (spit (fs/file src "runtime.cljc")
          (str "(ns t.runtime {:squint/compile-time true} (:require [clojure.string :as str]))\n"
               ;; a bare defmacro needs no marker; one in a #?(:clj ...) branch
               ;; loads only when explicitly marked
               "(defmacro twice [x] `(double-it ~x))\n"
               "#?(:clj ^:squint/compile-time (defmacro shout [x] `(str/upper-case ~x)))\n"
               ;; unmarked :clj code (JVM-only) is never loaded
               "#?(:clj (defmacro jvm-macro [] (System/getProperty \"x\")))\n"
               "#?(:clj (defn jvm-only [] (System/getenv \"HOME\")))\n"
               "(throw (js/Error. \"runtime ns evaluated - extraction leaked runtime\"))\n"
               "(defn double-it [x] (* 2 x))"))
    (spit (fs/file src "consumer.cljs")
          "(ns t.consumer (:require [t.runtime :as rt])) (defn a [] (rt/twice 21)) (defn b [] (rt/shout \"hi\")) (defn c [] (rt/jvm-macro))")
    (let [{:keys [exit out err]} (squint "compile")]
      (is (= 0 exit) (str "compile failed, runtime ns may have been evaluated: " err))
      (is (str/includes? out "Compiled sources: 2"))
      (let [consumer (slurp (fs/file test-dir "out" "t" "consumer.mjs"))
            runtime (slurp (fs/file test-dir "out" "t" "runtime.mjs"))]
        (is (str/includes? consumer "double_it(21)"))
        (is (str/includes? consumer "upper_case(\"hi\")"))
        (is (not (str/includes? consumer "twice(21)")))
        ;; the unmarked :clj defmacro is not loaded: the call does not expand
        (is (str/includes? consumer "jvm_macro()"))
        (is (not (str/includes? runtime "twice")))))))

(deftest analyzer-resolve-test
  ;; cljs.analyzer.api/resolve sees core vars, built-in macros and user
  ;; macros, with :macro true on macros. A name that is both a core var and
  ;; an inline macro (assoc) counts as a var, like in CLJS. Asserted on the
  ;; evaluated program, not on compiled text.
  (let [src (fs/file test-dir "src" "t")]
    (fs/create-dirs src)
    (spit (fs/file test-dir "squint.edn")
          (pr-str {:paths ["src"] :output-dir "out" :extension "mjs"}))
    (spit (fs/file src "macros.cljc")
          (str "(ns t.macros {:squint/compile-time true})\n"
               "(defmacro probe [sym] (pr-str ((resolve 'cljs.analyzer.api/resolve) &env sym)))\n"
               "(defmacro my-macro [x] x)"))
    (spit (fs/file src "consumer.cljs")
          (str "(ns t.consumer (:require [clojure.string :as str])\n"
               "  (:require-macros [t.macros :as m :refer [probe my-macro]]))\n"
               "(println (probe str/blank?))\n"
               "(println (probe clojure.string/blank?))\n"
               "(println (probe str/no-such-fn))\n"
               "(println (probe and))\n"
               "(println (probe bit-and))\n"
               "(println (probe when-let))\n"
               "(println (probe assoc))\n"
               "(println (probe inc))\n"
               "(println (probe my-macro))\n"
               "(println (probe m/my-macro))\n"
               "(println (probe t.macros/my-macro))\n"
               "(println (probe m/no-such-macro))\n"
               "(println (probe no-such-thing))\n"
               "(let [some-local 1 inc (fn [x] x)]\n"
               "  (println (probe some-local))\n"
               "  (println (probe inc)))\n"
               "(defn g [param] (println (probe param)) param)\n"
               "(g 1)"))
    (spit (fs/file src "excludes.cljs")
          (str "(ns t.excludes\n"
               "  (:refer-clojure :exclude [and juxt])\n"
               "  (:require-macros [t.macros :refer [probe]]))\n"
               "(println (probe and))\n"
               "(println (probe juxt))\n"
               "(def and 1)\n"
               "(println (probe and))"))
    (let [{:keys [exit err]} (squint "compile")]
      (is (= 0 exit) err)
      (is (= ["{:name clojure.string/blank?}"
              "{:name clojure.string/blank?}"
              "nil"
              "{:name cljs.core/and, :macro true}"
              "{:name cljs.core/bit-and, :macro true}"
              "{:name cljs.core/when-let, :macro true}"
              "{:name cljs.core/assoc}"
              "{:name cljs.core/inc}"
              "{:name t.macros/my-macro, :macro true}"
              "{:name t.macros/my-macro, :macro true}"
              "{:name t.macros/my-macro, :macro true}"
              "nil"
              "nil"
              "{:name some-local, :local true}"
              "{:name inc, :local true}"
              "{:name param, :local true}"]
             (str/split-lines
              (:out (p/shell {:dir test-dir :out :string} "node" "out/t/consumer.mjs")))))
      (is (= ["nil" "nil" "{:name t.excludes/and}"]
             (str/split-lines
              (:out (p/shell {:dir test-dir :out :string} "node" "out/t/excludes.mjs"))))))))

(defn run-tests [_]
  (let [{:keys [fail error]} (t/run-tests 'compile-time-tests)]
    (when (pos? (+ fail error))
      (throw (ex-info "Tests failed" {:babashka/exit 1})))))
