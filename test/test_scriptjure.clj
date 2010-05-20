(ns test-scriptjure
  (:use clojure.test)
  (:use com.reasonr.scriptjure)
  (:require [clojure.contrib.str-utils2 :as str]))

(defn strip-whitespace 
  "strip extraneous whitespace so tests don't fail because of differences in whitespace"
  [str]
  (str/trim (str/replace (str/replace str #"\n" " ") #"[ ]+" " ")))
  
(deftest number-literal
  (is (= (js 42) "42"))
  (is (= (js 1/2) "0.5")))

(deftest test-var-expr
  (is (= (js (var x 42)) "var x = 42")))

(deftest test-simple-funcall
  (is (= (js (a b)) "a(b)")))

(deftest test-funcall-multi-arg
  (is (= (js (a b c)) "a(b, c)")))

(deftest test-arithmetic 
  (is (= (js (* x y)) "(x * y)")))

(deftest test-return
  (is (= (strip-whitespace (js (return 42))) "return 42")))

(deftest test-clj
  (let [foo 42]
    (is (= (js (clj foo)) "42"))))
         
(deftest test-dot-fn-call
  (is (= (js (. foo bar :a :b)) "foo.bar(a, b)"))
  (is (= (js (. google.chart bar :a :b)) "google.chart.bar(a, b)")))

(deftest test-dot-method-call
  (is (= (js (.bar google.chart :a :b)) "google.chart.bar(a, b)")))

(deftest test-if
  (is (= (strip-whitespace (js (if (&& (== foo bar) (!= foo baz)) (.draw google.chart))))
         "if (((foo == bar) && (foo != baz))) { google.chart.draw() }"))
  (is (= (strip-whitespace (js (if foo (do (var x 3) (foo x)) (do (var y 4) (bar y)))))
         "if (foo) { var x = 3; foo(x); } else { var y = 4; bar(y); }")))
          
(deftest test-new-operator
  (is (= (js (new google.visualization.ColumnChart (.getElementById document "chart_div"))) "new google.visualization.ColumnChart(document.getElementById('chart_div'))")))

(deftest test-fn
  (is (= (strip-whitespace (js (fn foo [x] (foo a) (bar b)))) "function foo(x) { foo(a); bar(b); }")))

(deftest test-array
  (is (= (js [1 "2" :foo]) "[1, '2', foo]")))

(deftest test-aget
  (is (= (js (aget foo 2)) "foo[2]")))

(deftest test-map
  (is (= (strip-whitespace (js {:packages ["columnchart"]})) "{packages: ['columnchart']}")))

(deftest jquery
  (is (= (strip-whitespace (js (.ready ($j document) 
                                       (fn [] 
                                         (.bind ($j "div-id") "click" 
                                                (fn [e] 
                                                  (.cookie $j "should-display-make-public" true))))))) "$j(document).ready(function () { $j('div-id').bind('click', function (e) { $j.cookie('should-display-make-public', true); } ); } )" )))

(deftest test-do
  (is (= (strip-whitespace 
          (js 
           (var x 3)
           (var y 4)
           (+ x y))) "var x = 3; var y = 4; (x + y);")))

(deftest test-doseq
  (is (= (strip-whitespace (js (doseq [i [1 2 3]] (foo i))))
	 "for (i in [1 2 3]) { foo(i); }"))
  (is (= (strip-whitespace (js (doseq [i [1 2 3] j [4 5]] (foo i j))))
	 "for (i in [1 2 3]) { for (j in [4 5]) { foo(i, j); } }")))

(deftest test-combine-forms
  (let [stuff (js* (do
                       (var x 3)
                       (var y 4)))]
    (is (= (strip-whitespace (js (fn foo [x] (clj stuff))))
           "function foo(x) { var x = 3; var y = 4; }"))))

(deftest test-js*-adds-implicit-do
  (let [one (js* (var x 3)
                 (var y 4))
        two (js* (do
                   (var x 3)
                   (var y 4)))]
    (is (= (js (clj one)) (js (clj two))))))

(deftest test-cljs
  (let [foo 42]
    (is (= (cljs foo) (js (clj foo))))))

(deftest test-cljs*
  (let [foo (fn [] (+ 1 2))]
    (is (= (cljs* foo) (js* (clj foo))))))

(run-tests)