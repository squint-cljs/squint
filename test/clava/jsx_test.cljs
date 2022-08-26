(ns clava.jsx-test
  (:require
   [clava.test-utils :refer [eq js! jss! jsv!]]
   [clojure.string :as str]
   [clojure.test :as t :refer [async deftest is testing]]
   ["eslint" :refer [Linter]]))

(defn test-jsx [s]
  (let [expr (jss! s)
        _ (prn expr)
        results (js->clj (.verify (new Linter) expr #js {:parserOptions #js {:ecmaFeatures #js {:jsx true}}}) :keywordize-keys true)]
    (is (empty? results) results)))


(deftest jsx-test
  (test-jsx "#jsx [:a {:href \"http://foo.com\"}]")
  (test-jsx "#jsx [:div {:dangerouslySetInnerHTML {:_html \"<i>Hello</i>\"}}]")
  (test-jsx "(defn App [] (let [x 1] #jsx [:div {:id x}]))"))



