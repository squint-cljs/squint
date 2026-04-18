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

(deftest derive-with-hierarchy-is-immutable-test
  (t/async done
    (-> (eval-repl "
(let [h0 (make-hierarchy)
      h1 (derive h0 :a :b)
      h2 (derive h1 :c :b)]
  ;; mutating into h1 must not surface in h0, and h2 must not share
  ;; the parent-set of h1
  [(contains? (or (ancestors :a h0) #{}) :b)
   (contains? (or (ancestors :a h1) #{}) :b)
   (contains? (or (descendants :b h0) #{}) :c)
   (contains? (or (descendants :b h1) #{}) :c)
   (contains? (or (descendants :b h2) #{}) :c)])")
        (.then (fn [v]
                 (is (= [false true false false true] (vec v))
                     "3-arg derive must produce a fresh hierarchy")))
        (.finally done))))

(deftest derive-does-not-leak-to-tag-ancestors-test
  ;; Regression: _deriveInto's tagChain used to walk tag's ancestors,
  ;; so (derive :x :a) followed by (derive :x :b) incorrectly made :a
  ;; isa :b. Clojure's derive propagates the new relation to tag's
  ;; DESCENDANTS, not its ancestors.
  (t/async done
    (-> (eval-repl "
(derive :x :a)
(derive :x :b)
[(isa? :a :b) (isa? :b :a) (isa? :x :a) (isa? :x :b)]")
        (.then (fn [v] (is (= [false false true true] (vec v)))))
        (.finally done))))

(deftest two-arg-derive-busts-cache-test
  ;; Regression for PR feedback #1: 2-arg derive mutates
  ;; *global-hierarchy* in place, so the identity-based cache check in
  ;; MultiFn.getMethod doesn't fire. A value cached while only one isa
  ;; match existed keeps resolving to the stale fn after a subsequent
  ;; derive introduces ambiguity.
  (t/async done
    (-> (eval-repl "
(defmulti k identity)
(defmethod k :a [_] :a-fn)
(defmethod k :b [_] :b-fn)
(derive :x :a)
(let [first-call (k :x)
      _ (derive :x :b)  ;; now :x isa both :a and :b → ambiguous
      second-call (try (k :x) (catch :default e :threw))]
  [first-call second-call])")
        (.then (fn [v]
                 (is (= ["a-fn" "threw"] (vec v))
                     "2nd call must re-resolve and hit the ambiguity, not return cached :a-fn")))
        (.finally done))))

(deftest defmulti-accepts-plain-hierarchy-test
  ;; Regression for PR feedback #5: passing the raw result of
  ;; (make-hierarchy) as :hierarchy used to crash on first dispatch
  ;; because MultiFn called .deref() on a plain object. Now wrapped.
  (t/async done
    (-> (eval-repl "
(let [h (derive (make-hierarchy) :x :a)]
  (defmulti mm identity :hierarchy h)
  (defmethod mm :a [_] :a-fn)
  (mm :x))")
        (.then (fn [v] (is (= "a-fn" v))))
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
