(ns clava.jsx-test
  (:require
   ["@babel/core" :refer [transformSync]]
   ["React" :as React]
   [clava.test-utils :refer [jss!]]
   [clojure.test :as t :refer [deftest is]]
   [goog.object :as gobject]))

(gobject/set js/global "React" React)

(defn test-jsx [s]
  (let [expr (jss! s)
        code (:code (js->clj (transformSync expr #js {:presets #js ["@babel/preset-react"]}) :keywordize-keys true))]
    (js/eval code)))

(deftest jsx-test
  (test-jsx "#jsx [:a {:href \"http://foo.com\"}]")
  (test-jsx "#jsx [:div {:dangerouslySetInnerHTML {:_html \"<i>Hello</i>\"}}]")
  (test-jsx "(defn App [] (let [x 1] #jsx [:div {:id x}]))")
  (is (thrown? js/Error (test-jsx "#jsx [:a {:href 1}]")))
  (test-jsx "(let [classes {:classes \"foo bar\"}] #jsx [:div {:className (:classes classes)}])"))



