(ns squint.compiler-test
  (:require
   [babashka.fs :as fs]
   [babashka.process :refer [sh] :as p]
   [clojure.string :as str]
   [clojure.test :refer [deftest is] :as t]
   [squint.compiler :as sq]))

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
                     "true")))

(def our-ns *ns*)
(defn run-tests [_]
  (let [{:keys [fail error]}
        (t/run-tests our-ns)]
    (when (pos? (+ fail error))
      (throw (ex-info "Tests failed" {:babashka/exit 1})))))

(comment
  (test-expr "(prn (+ 1 2 3))")
  )
