(ns node-cli-tests
  "Sanity checks on cli error processing and squint.edn default handling"
  (:require
   [babashka.fs :as fs]
   [babashka.process :as p]
   [clojure.test :as t :refer [deftest is]]
   [clojure.string :as str]))

(def test-dir ".work/cli-test")

(t/use-fixtures :each (fn [f]
                        (when (fs/exists? test-dir)
                          (fs/delete-tree test-dir))
                        (fs/create-dirs test-dir)
                        (f)))

(defn squint-edn [cfg]
  (spit (fs/file test-dir "squint.edn") (pr-str cfg)))

(defn squint
  [& args]
  (apply p/shell {:continue true
                  :dir test-dir
                  :out :string
                  :err :string} ;; errors go to stderr (babashka.cli standard)
         (into ["node" "../../node_cli.js"] args)))

(deftest cmd-with-no-args-test
  (let [{:keys [exit err]} (squint "repl" "foo")]
    (is (= 1 exit))
    (is (str/includes? err "Error: Must specify no args, found: foo"))))

(deftest cmd-with-one-arg-test
  (let [{:keys [exit err]} (squint "run")]
    (is (= 1 exit))
    (is (str/includes? err "Error: Must specify a single <file>")))

  (let [{:keys [exit err]} (squint "run" "too" "many" "args")]
    (is (= 1 exit))
    (is (str/includes? err "Error: Must specify a single <file>, found: too many args"))))

(deftest unrecognized-opt-test
  (let [{:keys [exit err]} (squint "compile" "--wtf")]
    (is (= 1 exit))
    (is (str/includes? err "Unknown option: --wtf")))
  (let [{:keys [exit err]} (squint "compile" "-wtf")]
    (is (= 1 exit))
    (is (str/includes? err "Unknown option: -w"))))

(deftest opt-with-no-value-test
  (let [{:keys [exit err]} (squint "compile" "--paths")]
    (is (= 1 exit))
    (is (str/includes? err "Missing value for option --paths"))))

(deftest compile-is-assumed-when-not-specified-test
  ;; help alone brings up all cmds help
  (let [{:keys [exit out]} (squint "--help")]
    (is (= 0 exit))
    (is (str/includes? out "Usage: squint [options] <command>")))
  ;; if anything else not matching another cmd is specified, assume compile
  (let [{:keys [exit out]} (squint "some-file" "--help")]
    (is (= 0 exit))
    (is (str/includes? out "Usage: squint compile"))))

(deftest eval-test
  ;; -e is our only command-less command, so deserves sanity tests
  (let [{:keys [exit out]} (squint "-e" "(+ 1 2 3)" "--repl")]
    (is (= 0 exit))
    (is (= "6" (str/trim out))))
  (let [{:keys [exit out]} (squint "-e" "--help")]
    (is (= 0 exit))
    (is (str/includes? out "Usage: squint eval")))
  (let [{:keys [exit err]} (squint "-e")]
    (is (= 1 exit))
    (is (str/includes? err "Missing value for option -e"))))

(deftest no-files-processed-test
  (let [{:keys [exit out err]} (squint "compile" "wontfind")]
    (is (= 1 exit))
    (is (str/includes? err "Error: Compile processed no files"))
    (is (re-find #"Compiled sources: 0" out))
    (is (not (re-find #"Copied resources:" out))))
  (let [{:keys [exit out err]} (squint "compile" "wontfind" "--copy-resources" :foo)]
    (is (= 1 exit))
    (is (str/includes? err "Error: Compile processed no files"))
    (is (re-find #"Compiled sources: 0" out))
    (is (re-find #"Copied resources: 0" out))))

(deftest squint-edn-only-test
  (fs/copy-tree "test-project" test-dir)
  (fs/delete-tree (fs/file test-dir "lib"))
  (squint-edn {:paths ["src" "src-other"
                       "resources"]
               :output-dir "lib"
               :copy-resources #{"foo\\.json"  "test\\.json" :css}})
  (let [{:keys [exit out]} (squint "compile")]
    (is (= 0 exit))
    (doseq [s ["Compiled sources: 11"
               "Copied resources: 3"]]
      (is (str/includes? out s)))
    (doseq [f ["lib/foo.json"
               "lib/baz.css"
               "lib/main.mjs"]]
      (is (fs/exists? (str (fs/file test-dir f)))))
    (is (not (fs/exists? (str (fs/file test-dir "lib/bar.json")))))))

(deftest squint-edn-with-some-command-line-overrides-test
  (fs/copy-tree "test-project" test-dir)
  (fs/delete-tree (fs/file test-dir "lib"))
  (squint-edn {:paths ["src" "src-other"
                       "resources"]
               :output-dir "lib"
               :copy-resources #{"foo\\.json" "test\\.json" :css}})
  (let [{:keys [exit out]} (squint "compile"
                                   "--paths" "src" "--paths" "resources"
                                   "--output-dir" "other-lib")]
    (is (= 0 exit))
    (doseq [s ["Compiled sources: 10"
               "Copied resources: 3"]]
      (is (str/includes? out s)))
    (doseq [f ["other-lib/foo.json"
               "other-lib/baz.css"
               "other-lib/main.mjs"]]
      (is (fs/exists? (str (fs/file test-dir f)))))
    (is (not (fs/exists? (str (fs/file test-dir "other-lib/bar.json")))))))

(deftest squint-edn-with-all-command-line-overrides-test
  (fs/copy-tree "test-project" test-dir)
  (fs/delete-tree (fs/file test-dir "lib"))
  (squint-edn {:paths ["src" "src-other"
                       "resources"]
               :output-dir "lib"
               :copy-resources #{"foo\\.json" "test\\.json" :css}})
  (let [{:keys [exit out]} (squint "compile"
                                   "--paths" "src" "--paths" "resources"
                                   "--output-dir" "other-lib"
                                   "--copy-resources" "bar\\.json" "--copy-resources" ":css"
                                   "--extension" ".js")]
    (is (= 0 exit))
    (doseq [s ["Compiled sources: 10"
               "Copied resources: 2"]]
      (is (str/includes? out s)))
    (doseq [f ["other-lib/bar.json"
               "other-lib/baz.css"
               "other-lib/main.js"]]
      (is (fs/exists? (str (fs/file test-dir f)))))
    (is (not (fs/exists? (str (fs/file test-dir "other-lib/foo.json")))))))

(deftest compile-error-reports-location-test
  (fs/create-dirs (fs/file test-dir "src"))
  (spit (fs/file test-dir "src/bad.cljs") "(ns bad)\n(defn f [)\n")
  (let [{:keys [exit err]} (squint "compile" "src/bad.cljs")]
    (is (= 1 exit))
    (is (re-find #"Error while compiling .*bad\.cljs:2:\d+" err))
    (is (str/includes? err "Unmatched delimiter"))
    ;; friendly line only, no raw ExceptionInfo or node stack dump
    (is (not (str/includes? err "ExceptionInfo")))
    (is (not (str/includes? err "cljs-runtime")))))

(deftest compile-error-reports-transitive-macro-location-test
  (fs/create-dirs (fs/file test-dir "src"))
  (spit (fs/file test-dir "src/badmac.cljc")
        "(ns badmac)\n(defmacro m [x] (list 'inc x))\n(defn boom [ )\n")
  (spit (fs/file test-dir "src/main.cljs")
        "(ns main (:require [badmac :refer [m]]))\n(m 1)\n")
  (let [{:keys [exit err]} (squint "compile" "src/main.cljs")]
    (is (= 1 exit))
    ;; the failure is inside the transitively loaded macro ns, not the compiled file
    (is (re-find #"Error while compiling .*main\.cljs \(loading .*badmac\.cljc:3:\d+\)" err))
    (is (str/includes? err "Unmatched delimiter"))))

(defn run-tests [_]
  (let [{:keys [fail error]}
        (t/run-tests 'node-cli-tests)]
    (when (pos? (+ fail error))
      (throw (ex-info "Tests failed" {:babashka/exit 1})))))
