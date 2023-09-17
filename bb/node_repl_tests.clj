(ns node-repl-tests
  (:require
   [babashka.fs :as fs]
   [babashka.process :as p :refer [process]]
   [clojure.string :as str]
   [clojure.test :as t :refer [deftest is]]))

(defn repl-process
  [input dir opts]
  (process (into ["node" (str (fs/absolutize "node_cli.js")) "repl"] (:cmd opts))
           (merge {:dir (or dir ".")
                   :out :string
                   :in input
                   :err :inherit}
                  opts)))

(defn repl
  ([input] (repl input nil))
  ([input dir] (repl input dir nil))
  ([input dir opts]
   (-> (repl-process input dir opts)
       p/check)))

(deftest repl-test
  (is (str/includes? (:out (repl "1")) "1\n"))
  (is (str/includes? (:out (repl "\"foo\"")) "foo\n"))
  (is (str/includes? (:out (repl ":foo")) "foo\n"))
  (is (str/includes? (:out (repl "[1 2 3]")) "[ 1, 2, 3 ]\n"))
  (is (str/includes? (:out (repl "(+ 1 2 3)")) "6\n"))
  (is (str/includes? (:out (repl "(ns foo (:require [\"fs\" :as fs])) (fs/existsSync \".\")")) "true"))
  (is (str/includes? (:out (repl "(defn foo [x] x) (foo 1)")) "1"))
  (is (str/includes? (:out (repl "\"foo\"")) "foo"))
  (is (str/includes? (:out (repl "(ns foo (:require [\"playwright$default\" :as pw])) (let [chrome pw/chromium] (when (some? chrome) :success))")) "success"))
  (is (str/includes? (:out (repl "(require '[\"playwright$default\" :as pw]) (let [chrome pw/chromium] (when (some? chrome) :success))")) "success"))
  (is (str/includes? (:out (repl "(loop [] 1)")) "1"))
  (is (str/includes? (:out (repl "(defn ^:async foo [] (let [x (js-await (js/Promise.resolve 10))] (str \"the-answer\"(inc x)))) (foo)")) "the-answer11")))

(defn run-tests [_]
  (let [{:keys [fail error]}
        (t/run-tests 'node-repl-tests)]
    (when (pos? (+ fail error))
      (throw (ex-info "Tests failed" {:babashka/exit 1})))))
