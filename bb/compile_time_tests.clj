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

(defn run-tests [_]
  (let [{:keys [fail error]} (t/run-tests 'compile-time-tests)]
    (when (pos? (+ fail error))
      (throw (ex-info "Tests failed" {:babashka/exit 1})))))
