(ns squint.html-test
  (:require
   [clojure.test :as t :refer [deftest is]]
   [squint.test-utils :refer [jss!]]
   [squint.compiler :as squint]
   [clojure.string :as str]))

(deftest html-test
  (t/async done
    (is (str/includes?
         (jss! "#html [:div \"Hello\"]")
         "`<div>Hello</div>"))
    (is (str/includes?
         (jss! "#html ^foo/bar [:div \"Hello\"]")
         "foo.bar`<div>Hello</div>"))
    (let [{:keys [imports body]} (squint.compiler/compile-string* "(defn foo [x] #html [:div \"Hello\" x])")]
      (is (str/includes? imports "import * as squint_html from 'squint-cljs/src/squint/html.js'"))
      (is (str/includes? body "squint_html.tag`<div>Hello${x}</div>`")))
    (let [js (squint.compiler/compile-string "
(defn li [x] #html [:li x])
(defn foo [x] #html [:ul (map #(li %) (range x))]) (foo 5)" {:repl true :elide-exports true :context :return})
          js (str/replace "(async function() { %s } )()" "%s" js)]
      (-> (js/eval js)
          (.then
           #(is (= "<ul><li>0</li><li>1</li><li>2</li><li>3</li><li>4</li></ul>" %)))
          (.catch #(is false "nooooo"))
          (.finally done)))))
