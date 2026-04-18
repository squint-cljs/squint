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

(deftest hierarchy-accessors-equiv-on-vector-tags-test
  ;; Regression for the uneven value-equality fix: addRel / isa? /
  ;; derive all got the findKeyByEquiv treatment, but the hAnd reader
  ;; behind parents / ancestors / descendants was still doing raw
  ;; h[field].get(tag). So (derive h [:km :m] :length) + (parents h
  ;; [:km :m]) returned nil even though isa? saw the relation.
  ;;
  ;; Using `some #{target}` / explicit `=` instead of `contains?`
  ;; because squint Sets are reference-equal for non-primitives —
  ;; orthogonal issue from the accessor bug we're pinning here.
  (t/async done
    (-> (eval-repl "
(let [h (-> (make-hierarchy) (derive [:vec/km :vec/m] :vec/length))]
  [(isa? h [:vec/km :vec/m] :vec/length)
   ;; fresh-identity vector — must still hit the stored entry
   (boolean (some #{:vec/length} (parents h [:vec/km :vec/m])))
   (boolean (some #{:vec/length} (ancestors h [:vec/km :vec/m])))
   (boolean (some (fn [d] (= d [:vec/km :vec/m]))
                  (descendants h :vec/length)))])")
        (.then (fn [v] (is (= [true true true true] (vec v)))))
        (.finally done))))

(deftest prefer-method-vector-dispatch-equiv-test
  ;; Regression: prefer-method used reference equality on preferTable
  ;; keys/values, so two prefer-method calls with freshly-allocated
  ;; vectors [:a :x] wouldn't see each other — the cycle check never
  ;; fired, and dispatch preference didn't take effect.
  (t/async done
    (-> (eval-repl "
(defmulti conv identity)
(defmethod conv [:pref/a] [_] :a)
(defmethod conv [:pref/b] [_] :b)
(prefer-method conv [:pref/a] [:pref/b])
;; second call with a FRESH vector pair that forms a cycle — must throw
(let [cycle (try (prefer-method conv [:pref/b] [:pref/a]) :allowed
                 (catch :default _ :threw))
      ;; and the preference must be readable back via a fresh vector
      pref-map (prefers conv)
      seen? (boolean (some (fn [[k _]] (= k [:pref/a])) pref-map))]
  [cycle seen?])")
        (.then (fn [v] (is (= ["threw" true] (vec v)))))
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

(deftest hierarchy-accessors-follow-clojure-signature-test
  ;; Regression: parents / ancestors / descendants had reversed args
  ;; (tag first, hierarchy second). Clojure's canonical signature is
  ;; (f tag) or (f h tag) — hierarchy FIRST when given. The previous
  ;; tests wrote (ancestors tag h) and matched the buggy impl.
  (t/async done
    (-> (eval-repl "
(let [h (-> (make-hierarchy) (derive :sig/child :sig/parent))]
  [;; 2-arg form: hierarchy first
   (contains? (or (ancestors h :sig/child) #{}) :sig/parent)
   (contains? (or (descendants h :sig/parent) #{}) :sig/child)
   (contains? (or (parents h :sig/child) #{}) :sig/parent)
   ;; 1-arg form: uses global hierarchy
   (do (derive :sig/g-child :sig/g-parent) true)
   (contains? (or (ancestors :sig/g-child) #{}) :sig/g-parent)
   (contains? (or (descendants :sig/g-parent) #{}) :sig/g-child)])")
        (.then (fn [v]
                 (is (= [true true true true true true] (vec v)))))
        (.finally done))))

(deftest derive-with-hierarchy-is-immutable-test
  (t/async done
    (-> (eval-repl "
(let [h0 (make-hierarchy)
      h1 (derive h0 :a :b)
      h2 (derive h1 :c :b)]
  ;; mutating into h1 must not surface in h0, and h2 must not share
  ;; the parent-set of h1
  [(contains? (or (ancestors h0 :a) #{}) :b)
   (contains? (or (ancestors h1 :a) #{}) :b)
   (contains? (or (descendants h0 :b) #{}) :c)
   (contains? (or (descendants h1 :b) #{}) :c)
   (contains? (or (descendants h2 :b) #{}) :c)])")
        (.then (fn [v]
                 (is (= [false true false false true] (vec v))
                     "3-arg derive must produce a fresh hierarchy")))
        (.finally done))))

(deftest derive-does-not-leak-to-tag-ancestors-test
  ;; Regression: _deriveInto's tagChain used to walk tag's ancestors,
  ;; so (derive :x :a) followed by (derive :x :b) incorrectly made :a
  ;; isa :b. Clojure's derive propagates the new relation to tag's
  ;; DESCENDANTS, not its ancestors.
  ;;
  ;; Unique tags per test: the global hierarchy is shared across tests
  ;; in this ns, so reusing keywords would cross-pollute and break CI
  ;; in whatever order shadow-cljs happens to emit.
  (t/async done
    (-> (eval-repl "
(derive :leak/x :leak/a)
(derive :leak/x :leak/b)
[(isa? :leak/a :leak/b) (isa? :leak/b :leak/a)
 (isa? :leak/x :leak/a) (isa? :leak/x :leak/b)]")
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
(defmulti k-cache identity)
(defmethod k-cache :cache/a [_] :a-fn)
(defmethod k-cache :cache/b [_] :b-fn)
(derive :cache/x :cache/a)
(let [first-call (k-cache :cache/x)
      _ (derive :cache/x :cache/b)  ;; now :cache/x isa both → ambiguous
      second-call (try (k-cache :cache/x) (catch :default e :threw))]
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
