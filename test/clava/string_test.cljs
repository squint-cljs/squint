(ns clava.string-test
  (:require
   ["fs" :as fs]
   ["path" :as path]
   [clava.compiler :as compiler]
   [clojure.test :as t :refer [async deftest is]]))

(defn compile! [str-or-expr]
  (let [s (if (string? str-or-expr)
            str-or-expr
            (pr-str str-or-expr))]
    (compiler/compile-string s)))

(def dyn-import (js/eval (js* "(x) => import(x)")))

(deftest blank?-test
  (async done
   (let [prog (compile! '(do (ns foo (:require [clava.string :as str]))
                             (def result (str/blank? ""))))]
     (fs/writeFileSync "test.mjs" prog)
     (.then (dyn-import (path/resolve (js/process.cwd) "test.mjs"))
            #(do (is (.-result %))
                 (done))))))



