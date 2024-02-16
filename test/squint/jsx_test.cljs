(ns squint.jsx-test
  (:require
   ["@babel/core" :refer [transformSync]]
   ["react" :as React]
   ["react-dom/server" :as Rdom]
   [clojure.test :as t :refer [deftest is]]
   [goog.object :as gobject]
   [squint.test-utils :refer [jss!]]
   #_:clj-kondo/ignore
   ["fs" :as fs]
   #_:clj-kondo/ignore
   ["path" :as path]
   ["squint-cljs/core.js" :as squint_core]
   [clojure.string :as str]))

(gobject/set js/global "React" React)
(gobject/set js/global "Rdom" Rdom)
(gobject/set js/global "squint_core" squint_core)

(defn test-jsx [s]
  (let [expr (jss! s)
        code (:code (js->clj (transformSync expr #js {:presets #js ["@babel/preset-react"]}) :keywordize-keys true))]
    ;; (println s)
    ;; (prn (js/eval code))
    (Rdom/renderToString (js/eval code))))

(def testing (constantly nil))

(deftest jsx-test
  (is (= "<a href=\"http://foo.com\"></a>"
         (test-jsx "#jsx [:a {:href \"http://foo.com\"}]")))
  (is (= "<div><i>Hello</i></div>"
         (test-jsx "#jsx [:div {:dangerouslySetInnerHTML {:__html \"<i>Hello</i>\"}}]")))
  (is (= "<div id=\"1\"></div>"
         (test-jsx "(defn App [] (let [x 1] #jsx [:div {:id x}])) #jsx [App]")))
  (is (= "<div class=\"foo bar\"></div>"
         (test-jsx "(let [classes {:classes \"foo bar\"}] #jsx [:div {:className (:classes classes)}])")))
  (is (= "<span>1</span>" (test-jsx "(defn App [{:keys [x]}] #jsx [:span x]) #jsx [App {:x 1}]")))
  (test-jsx "
(ns foo (:require [\"foo\" :as foo]))
(defn App [] #jsx [foo/c #js {:x 1}]) ")
  (is (= "<div data-foo=\"true\"></div>"
         (test-jsx "(defn TextField [{:keys [multiline]}] #jsx [:div {:data-foo multiline}])
(TextField {:multiline true})")))
  (testing "spread"
    (let [s (test-jsx "(defn TextField [{:keys [multiline]}]
                         (let [m {:a 1}]
                           #jsx [:div {:foo 1 :& m} \"Hello\"]))
                       (TextField {:multiline true})")]
      (is (= "<div foo=\"1\" a=\"1\">Hello</div>" s)))
    (test-jsx "(defn TextField [{:keys [multiline]}])
                 (let [m {:a 1}] #jsx [TextField {:& m :foo :bar}])")
    (test-jsx "(defn TextField []) #jsx [TextField {:styles [1 2 3]}]"))
  (let [s (jss! "#jsx [:View [:Text \"Hello\"][:Text \"World! \"]]")]
    (is (str/includes? s "</Text><Text>"))
    (is (str/includes? s "<Text>World! </Text>")))
  (testing "conditional"
    (let [cljs "(defn App []
      (let [picked-emoji true]
        #jsx [:div {}
              (if picked-emoji
                #jsx [:div \"Picker\"]
                #jsx [:div \"Not picked\"])]))
      (App)"
          s (jss! cljs)]
      (is (str/includes? s "<div>{(squint_core.truth_(picked_emoji1))"))
      (is (= "<div><div>Picker</div></div>" (test-jsx cljs)))))
  (testing "less than, greater than"
    (is (= "<div>&lt;&gt;</div>" (test-jsx "#jsx [:div \"<>\"]"))))
  (testing "keyword components should render with hyphen"
    (is (= "<foo-bar data-foo-bar=\"true\"></foo-bar>" (test-jsx "#jsx [:foo-bar {:data-foo-bar true}]")))))



