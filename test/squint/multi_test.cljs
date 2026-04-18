(ns squint.multi-test
  (:require
   [clojure.string :as str]
   [clojure.test :as t :refer [deftest is]]
   [squint.compiler :as squint]
   [squint.test-utils :refer [jss!]]))

(defn- eval-repl [src]
  (let [js (squint/compile-string src {:repl true :elide-exports true :context :return})
        js (str/replace "(async function() { %s } )()" "%s" js)]
    (js/eval js)))

(deftest no-import-when-unused
  (let [{:keys [imports]} (squint/compile-string* "(+ 1 2) (defn f [x] (inc x))")]
    (is (not (str/includes? imports "squint/multi.js")))))

(deftest import-when-defmulti-used
  (let [{:keys [imports body]} (squint/compile-string* "(defmulti area :shape)")]
    (is (str/includes? imports "import * as squint_multi from 'squint-cljs/src/squint/multi.js'"))
    (is (str/includes? body "squint_multi.defmulti(\"area\""))))

(deftest defmulti-dispatch-test
  (t/async done
    (-> (eval-repl "
(defmulti area :shape)
(defmethod area :square [{:keys [side]}] (* side side))
(defmethod area :circle [{:keys [r]}] (* 2 r r))
(defmethod area :default [_] :dunno)
[(area {:shape :square :side 3})
 (area {:shape :circle :r 2})
 (area {:shape :triangle})]")
        (.then (fn [v] (is (= [9 8 "dunno"] (vec v)))))
        (.finally done))))

(deftest hierarchy-test
  (t/async done
    (-> (eval-repl "
(derive :rect :shape)
(derive :square :rect)
(defmulti k identity)
(defmethod k :shape [_] :shape)
(defmethod k :square [_] :square)
[(k :square) (k :rect) (isa? :square :shape)]")
        (.then (fn [v] (is (= ["square" "shape" true] (vec v)))))
        (.finally done))))

(deftest prefer-method-test
  (t/async done
    (-> (eval-repl "
(derive :dog :animal)
(derive :dog :pet)
(defmulti describe identity)
(defmethod describe :animal [_] :animal)
(defmethod describe :pet [_] :pet)
(prefer-method describe :pet :animal)
(describe :dog)")
        (.then (fn [v] (is (= "pet" v))))
        (.finally done))))

(deftest vector-dispatch-and-remove-test
  (t/async done
    (-> (eval-repl "
(defmulti conv (fn [from to _] [from to]))
(defmethod conv [:km :m] [_ _ x] (* x 1000))
(defmethod conv [:m :cm] [_ _ x] (* x 100))
(let [before (count (methods conv))
      a (conv :km :m 5)
      _ (remove-method conv [:m :cm])
      after (count (methods conv))]
  [a before after])")
        (.then (fn [v] (is (= [5000 2 1] (vec v)))))
        (.finally done))))

(deftest no-matching-method-test
  (t/async done
    (-> (eval-repl "
(defmulti f :t)
(defmethod f :a [_] :a)
(try (f {:t :b}) (catch :default e (.-message e)))")
        (.then (fn [msg]
                 (is (str/includes? msg "No method"))))
        (.finally done))))
