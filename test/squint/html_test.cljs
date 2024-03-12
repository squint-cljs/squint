(ns squint.html-test
  (:require
   [clojure.test :as t :refer [deftest is]]
   [squint.test-utils :refer [jss!]]
   [clojure.string :as str]))

(deftest html-test
  (is (str/includes?
       (jss! "#html [:div \"Hello\"]")
       "`<div>Hello</div>")))



