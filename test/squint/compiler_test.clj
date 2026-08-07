(ns squint.compiler-test
  (:require
   [babashka.fs :as fs]
   [babashka.process :refer [sh] :as p]
   [clojure.string :as str]
   [clojure.test :refer [deftest is] :as t]
   [clojure.edn :as edn]
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

(def our-ns *ns*)
(defn run-tests [_]
  (let [{:keys [fail error]}
        (t/run-tests our-ns)]
    (when (pos? (+ fail error))
      (throw (ex-info "Tests failed" {:babashka/exit 1})))))

(comment
  (test-expr "(prn (+ 1 2 3))")
  )
