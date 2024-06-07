(ns squint.html-test
  (:require
   [clojure.test :as t :refer [deftest is]]
   [squint.test-utils :refer [jss! jsv!]]
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
      (is (str/includes? body "squint_html.tag`<div>Hello${squint_html._safe(x)}</div>")))
    (let [js (squint.compiler/compile-string "
(defn li [x] #html [:li x])
(defn foo [x] #html [:ul (map #(li %) (range x))]) (foo 5)" {:repl true :elide-exports true :context :return})
          js (str/replace "(async function() { %s } )()" "%s" js)]
      (-> (js/eval js)
          (.then
           #(is (= "<ul><li>0</li><li>1</li><li>2</li><li>3</li><li>4</li></ul>" %)))
          (.catch #(is false "nooooo"))
          (.finally done)))))

(deftest html-attrs-test
  (t/async done
    (let [js (squint.compiler/compile-string
              "#html [:div {:class \"foo\" :id (+ 1 2 3)
                            :style {:color :green}}]"
                                             {:repl true :elide-exports true :context :return})
          js (str/replace "(async function() { %s } )()" "%s" js)]
      (-> (js/eval js)
          (.then
           #(is (= "<div class=\"foo\" id=\"6\" style=\"color:green;\"></div>" %)))
          (.catch #(is false "nooooo"))
          (.finally done)))))

(deftest html-nil-test
  (t/async done
    (let [js (squint.compiler/compile-string
              "(let [p nil] #html [:div p])"
              {:repl true :elide-exports true :context :return})
          js (str/replace "(async function() { %s } )()" "%s" js)]
      (-> (js/eval js)
          (.then
           #(is (= "<div>undefined</div>" %)))
          (.catch #(is false "nooooo"))
          (.finally done)))))

(deftest html-props-test
  (t/async done
    (let [js (squint.compiler/compile-string
              "(let [m {:a 1 :b 2}] #html [:div {:& m :a 2 :style {:color :red}} \"Hello\"])"
              {:repl true :elide-exports true :context :return})
          js (str/replace "(async function() { %s } )()" "%s" js)]
      (-> (js/eval js)
          (.then
           #(is (= "<div a=\"1\" style=\"color:red;\" b=\"2\">Hello</div>" %)))
          (.catch #(is false "nooooo"))
          (.finally done)))))

(deftest html-dynamic-css-test
  (t/async done
    (let [js (squint.compiler/compile-string
              "(let [m {:color :green}] #html [:div {:style (assoc m :width \"200\")} \"Hello\"])"
              {:repl true :elide-exports true :context :return})
          js (str/replace "(async function() { %s } )()" "%s" js)]
      (-> (js/eval js)
          (.then
           #(is (=  "<div style=\"color:green; width:200;\">Hello</div>" %)))
          (.catch #(is false "nooooo"))
          (.finally done)))))

(deftest html-safe-test
  (t/async done
    (let [js (squint.compiler/compile-string
              "(defn foo [x] #html [:div x]) (foo \"<>\")"
              {:repl true :elide-exports true :context :return})
          js (str/replace "(async function() { %s } )()" "%s" js)]
      (-> (js/eval js)
          (.then
           #(is (= "<div>&lt;&gt;</div>" %)))
          (.catch #(is false "nooooo"))
          (.finally done)))))
