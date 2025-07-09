(ns squint.html-test
  (:require
   [clojure.test :as t :refer [deftest is]]
   [squint.test-utils :refer [jss!]]
   [squint.compiler :as squint]
   [clojure.string :as str]
   [promesa.core :as p]))

(defn html= [x y]
  (= (str x) (str y)))

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
      (is (str/includes? body "squint_html.tag`<div>Hello${x}</div>")))
    (let [js (squint.compiler/compile-string "
(defn li [x] #html [:li x])
(defn foo [x] #html [:ul (map #(li %) (range x))]) (foo 5)" {:repl true :elide-exports true :context :return})
          js (str/replace "(async function() { %s } )()" "%s" js)]
      (-> (js/eval js)
          (.then
           #(is (html= "<ul><li>0</li><li>1</li><li>2</li><li>3</li><li>4</li></ul>" %)))
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
           #(is (html= "<div class=\"foo\" id=\"6\" style=\"color:green;\"></div>" %)))
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
           #(is (html= "<div>undefined</div>" %)))
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
           #(is (html= "<div a=\"1\" style=\"color:red;\" b=\"2\">Hello</div>" %)))
          (.catch #(is false "nooooo"))
          (.finally done)))))

(deftest html-dynamic-css-test
  (t/async done
    (let [js (squint.compiler/compile-string
              "(let [m {:color :green} m (assoc m :width \"200\")] #html [:div {:style {:& m}} \"Hello\"])"
              {:repl true :elide-exports true :context :return})
          js (str/replace "(async function() { %s } )()" "%s" js)]
      (-> (js/eval js)
          (.then
           #(is (html= "<div style=\"color:green; width:200;\">Hello</div>" %)))
          (.catch #(is false "nooooo"))
          (.finally done)))))

(deftest html-fragment-test
  (t/async done
    (let [js (squint.compiler/compile-string
              "#html [:div [:<> \"Hello\"]]"
              {:repl true :elide-exports true :context :return})
          js (str/replace "(async function() { %s } )()" "%s" js)]
      (-> (js/eval js)
          (.then
           #(is (html= "<div>Hello</div>" %)))
          (.catch #(do
                     (js/console.log %)
                     (is false "nooooo")))
          (.finally done)))))

(defn compile-html [s]
  (let [js (squint.compiler/compile-string s
                                           {:repl true :elide-exports true :context :return})
        js (str/replace "(async function() { %s } )()" "%s" js)]
    js))

(deftest html-safe-test
  (t/async done
    (->
     (p/do
       (p/let [js (compile-html
                   "(defn foo [x] #html [:div x]) (foo \"<>\")")
               v (js/eval js)
               _ (is (html= "<div>&lt;&gt;</div>" v))])
       (p/let [js (compile-html
                   "(defn foo [x] #html [:div x]) (defn bar [] #html [:div (foo \"<script>\")])
                    (bar)")
               v (js/eval js)
               _ (is (html= "<div><div>&lt;script&gt;</div></div>" v))]))
     (p/catch #(is false "nooooo"))
     (p/finally done))))

(deftest html5-test
  (t/async done
    (->
     (p/do
       (p/let [js (compile-html "#html [:div [:br]]")
               v (js/eval js)
               _ (is (html= "<div><br></div>" v))])
       )
     (p/catch #(is false "nooooo"))
     (p/finally done))))

(deftest html-id-class-shortcut-test
  (t/async done
    (let [js (squint.compiler/compile-string
              "[(str #html [:div.container])
                (str #html [:a#foo.bar.baz {:class \"quux\"}])]"
              {:repl true :elide-exports true :context :return})
          js (str/replace "(async function() { %s } )()" "%s" js)]
      (-> (js/eval js)
          (.then
           #(doseq [[k v] (map vector ["<div class=\"container\"></div>"
                                       "<a class=\"bar baz quux\" id=\"foo\"></a>"] %)]
              (is (html= k v))))
          (.catch #(is false "nooooo"))
          (.finally done)))))

(deftest html-unsafe-test
  (t/async done
    (let [js (squint.compiler/compile-string
              "(str #html [:div [:$ \"<><><>\" (js/JSON.stringify {:a (+ 1 2 3)})]])"
              {:repl true :elide-exports true :context :return})
          js (str/replace "(async function() { %s } )()" "%s" js)]
      (-> (js/eval js)
          (.then
           (fn [v]
             (is (= "<div><><><>{\"a\":6}</div>" v))))
          (.catch #(is false "nooooo"))
          (.finally done)))))
