(ns expr-harness
  (:require [babashka.fs :as fs]
            [babashka.process :refer [shell]]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [squint.compiler :as squint]))

(def ^:private opts
  {:elide-imports true :elide-exports true :top-level false :context :expr})

(defn run
  "Compiles script/expr_harness/forms.edn and evaluates each form in node,
  directly and through Datastar's value attribute splitter. Pass --strict to
  fail when a form breaks in the splitter."
  [{:keys [strict]}]
  (let [forms (edn/read-string (slurp "script/expr_harness/forms.edn"))
        out   (fs/file ".work" "expr-harness" "compiled.json")]
    (fs/create-dirs (fs/parent out))
    (spit out (json/generate-string
               (for [[src expected] forms]
                 {:src src :expected expected :js (squint/compile-string src opts)})))
    (apply shell "node" "script/expr_harness/eval.js" (str out)
           (when strict ["--strict"]))))
