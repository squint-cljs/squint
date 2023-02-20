;; Adapted from CLJS core.cljc. Original copyright notice:

;;   Copyright (c) Rich Hickey. All rights reserved.  The use and distribution
;;   terms for this software are covered by the Eclipse Public License
;;   1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in
;;   the file epl-v10.html at the root of this distribution.  By using this
;;   software in any fashion, you are agreeing to be bound by the terms of this
;;   license.  You must not remove this notice, or any other, from this
;;   software.

(ns squint.internal.macros
  (:require [clojure.string :as str]))

(defn core->
  [_ _ x & forms]
  (loop [x x, forms forms]
    (if forms
      (let [form (first forms)
            threaded (if (seq? form)
                       (with-meta `(~(first form) ~x ~@(next form)) (meta form))
                       (list form x))]
        (recur threaded (next forms)))
      x)))

(defn core->>
  [_ _ x & forms]
  (loop [x x, forms forms]
    (if forms
      (let [form (first forms)
            threaded (if (seq? form)
                       (with-meta `(~(first form) ~@(next form)  ~x) (meta form))
                       (list form x))]
        (recur threaded (next forms)))
      x)))

(defn core-as->
  [_ _ expr name & forms]
  `(let [~name ~expr
         ~@(interleave (repeat name) (butlast forms))]
     ~(if (empty? forms)
        name
        (last forms))))

(defn core-comment
  [_ _ & _body])

(defn core-dotimes
  [_ _ bindings & body]
  (assert (vector? bindings))
  (assert (= 2 (count bindings)))
  (let [i (first bindings)
        n (second bindings)]
    `(let [n# (long ~n)]
       (loop [~i 0]
         (when (< ~i n#)
           ~@body
           (recur (unchecked-inc ~i)))))))

(defn core-if-not
  "if-not from clojure.core"
  ([&form &env test then] (core-if-not &form &env test then nil))
  ([_&form _&env test then else]
   `(if (not ~test) ~then ~else)))

(defn core-when
  [_ _ test & body]
  (list 'if test (cons 'do body)))

(defn core-when-not
  "when-not from clojure.core"
  [_&form _&env test & body]
  (list 'if test nil (cons 'do body)))

(defn core-doto
  "doto from clojure.core"
  [_&form _&env x & forms]
  (let [gx (gensym)]
    `(let [~gx ~x]
       ~@(map (fn [f]
                (with-meta
                  (if (seq? f)
                    `(~(first f) ~gx ~@(next f))
                    `(~f ~gx))
                  (meta f)))
              forms)
       ~gx)))

(defn core-cond
  [_ _ & clauses]
  (when clauses
    (list 'if (first clauses)
          (if (next clauses)
            (second clauses)
            (throw (new #?(:clj IllegalArgumentException
                           :cljs js/Error)
                        "cond requires an even number of forms")))
          (cons 'clojure.core/cond (next (next clauses))))))

(defn core-cond->
  [_&form _&env expr & clauses]
  (assert (even? (count clauses)))
  (let [g (gensym)
        steps (map (fn [[test step]] `(if ~test (-> ~g ~step) ~g))
                   (partition 2 clauses))]
    `(let [~g ~expr
           ~@(interleave (repeat g) (butlast steps))]
       ~(if (empty? steps)
          g
          (last steps)))))

(defn core-cond->>
  [_&form _&env expr & clauses]
  (assert (even? (count clauses)))
  (let [g (gensym)
        steps (map (fn [[test step]] `(if ~test (->> ~g ~step) ~g))
                   (partition 2 clauses))]
    `(let [~g ~expr
           ~@(interleave (repeat g) (butlast steps))]
       ~(if (empty? steps)
          g
          (last steps)))))

(defn core-if-let
  ([&form &env bindings then]
   (core-if-let &form &env bindings then nil))
  ([_&form _&env bindings then else & _oldform]
   (let [form (bindings 0) tst (bindings 1)]
     `(let [temp# ~tst]
        (if temp#
          (let [~form temp#]
            ~then)
          ~else)))))

(defn core-if-some
  ([&form &env bindings then]
   (core-if-some &form &env bindings then nil))
  ([_&form _&env bindings then else & _oldform]
   (let [form (bindings 0) tst (bindings 1)]
     `(let [temp# ~tst]
        (if (nil? temp#)
          ~else
          (let [~form temp#]
            ~then))))))

(defn core-when-let
  [_&form _&env bindings & body]
  (let [form (bindings 0) tst (bindings 1)]
    `(let [temp# ~tst]
       (when temp#
         (let [~form temp#]
           ~@body)))))

(defn core-when-first [_ _ bindings & body]
  (let [[x xs] bindings]
    `(when-let [xs# (seq ~xs)]
       (let [~x (first xs#)]
         ~@body))))

(defn core-when-some [_ _ bindings & body]
  (let [form (bindings 0) tst (bindings 1)]
    `(let [temp# ~tst]
       (if (nil? temp#)
         nil
         (let [~form temp#]
           ~@body)))))

(defn core-some->
  [_&form _&env expr & forms]
  (let [g (gensym)
        steps (map (fn [step] `(if (nil? ~g) nil (-> ~g ~step)))
                   forms)]
    `(let [~g ~expr
           ~@(interleave (repeat g) (butlast steps))]
       ~(if (empty? steps)
          g
          (last steps)))))

(defn core-some->>
  [_ _ expr & forms]
  (let [g (gensym)
        steps (map (fn [step] `(if (nil? ~g) nil (->> ~g ~step)))
                   forms)]
    `(let [~g ~expr
           ~@(interleave (repeat g) (butlast steps))]
       ~(if (empty? steps)
          g
          (last steps)))))


(defn core-lazy-seq
  "Takes a body of expressions that returns an ISeq or nil, and yields
  a ISeqable object that will invoke the body only the first time seq
  is called, and will cache the result and return it on all subsequent
  seq calls."
  [_ _ & body]
  `(new cljs.core/LazySeq (fn [] ~@body)))

(defn core-for
  "List comprehension. Takes a vector of one or more
   binding-form/collection-expr pairs, each followed by zero or more
   modifiers, and yields a lazy sequence of evaluations of expr.
   Collections are iterated in a nested fashion, rightmost fastest,
   and nested coll-exprs can refer to bindings created in prior
   binding-forms.  Supported modifiers are: :let [binding-form expr ...],
   :while test, :when test.
  (take 100 (for [x (range 100000000) y (range 1000000) :while (< y x)]  [x y]))"
  [_ _ seq-exprs body]
  (let [err (fn [& msg] (throw (ex-info (apply str msg) {})))
        step (fn step [exprs]
               (if-not exprs
                 [true (list 'js* "yield ~{}" body)]
                 (let [k (first exprs)
                       v (second exprs)
                       steppair (step (nnext exprs))
                       needrec (steppair 0)
                       subform (steppair 1)]
                   (cond
                     (= k :let) [needrec `(let ~v ~subform)]
                     (= k :while) [false
                                   ;; emit literal JS because `if` detects that
                                   ;; it's an expr context and emits a ternary,
                                   ;; but you can't break inside of a ternary
                                   (list 'js*
                                         "if (~{}) {\n~{}\n} else { break; }"
                                         v subform)]
                     (= k :when) [false `(when ~v
                                           ~subform)]
                     (keyword? k) (err "Invalid 'for' keyword" k)
                     :else [true (list 'js* "for (let ~{} of ~{}) {\n~{}\n}"
                                       k v subform)]))))]
    (list 'lazy (list 'js* "function* () {\n~{}\n}"
                      (nth (step (seq seq-exprs)) 1)))))

(defn core-doseq
  "Repeatedly executes body (presumably for side-effects) with
  bindings and filtering as provided by \"for\".  Does not retain
  the head of the sequence. Returns nil."
  [_ _ seq-exprs & body]
  #_(assert-args doseq
                 (vector? seq-exprs) "a vector for its binding"
                 (even? (count seq-exprs)) "an even number of forms in binding vector")
  (let [err (fn [& msg] (throw (ex-info (apply str msg) {})))
        step (fn step [exprs]
               (if-not exprs
                 [true `(do ~@body)]
                 (let [k (first exprs)
                       v (second exprs)
                       steppair (step (nnext exprs))
                       needrec (steppair 0)
                       subform (steppair 1)]
                   (cond
                     (= k :let) [needrec `(let ~v ~subform)]
                     (= k :while) [false `(if ~v
                                            ~subform
                                            (~'js* "break;\n"))]
                     (= k :when) [false `(when ~v
                                           ~subform)]
                     (keyword? k) (err "Invalid 'doseq' keyword" k)
                     :else [true (list 'js* "for (let ~{} of ~{}) {\n~{}\n}"
                                       k v subform)]))))]
    (nth (step (seq seq-exprs)) 1)))

(defn core-defonce
  "defs name to have the root value of init iff the named var has no root value,
  else init is unevaluated"
  [_&form _&env x init]
  (let [qualified (if (namespace x)
                    x
                    x
                    ;; TODO:
                    #_(symbol (str (-> &env :ns :name)) (name x)))]
    `(when-not (exists? ~qualified)
       (def ~x ~init))))

(defn- bool-expr [e]
  (vary-meta e assoc :tag 'boolean))

(defn core-exists?
  "Return true if argument exists, analogous to usage of typeof operator
   in JavaScript."
  [_ _&env x]
  (if (symbol? x)
    (let [x     (cond-> x #_(:name nil
                                   ;; TODO
                                   #_(cljs.analyzer/resolve-var &env x))
                        (= "js" (namespace x)) name)
          segs  (str/split (str (str/replace-first (str x) "/" ".")) #"\.")
          n     (count segs)
          syms  (map
                 #(vary-meta (symbol "js" (str/join "." %))
                             assoc :cljs.analyzer/no-resolve true)
                 (reverse (take n (iterate butlast segs))))
          js    (str/join " && " (repeat n "(typeof ~{} !== 'undefined')"))]
      (bool-expr (concat (list 'js* js) syms)))
    `(some? ~x)))

(defn- assoc-test [m test expr env]
  (if (contains? m test)
    (throw
     #?(:clj (IllegalArgumentException.
              (str "Duplicate case test constant '"
                   test "'"
                   (when (:line env)
                     (str " on line " (:line env) " "
                          #_cljs.analyzer/*cljs-file*))))
        :cljs (js/Error.
               (str "Duplicate case test constant '"
                    test "'"
                    (when (:line env)
                      (str " on line " (:line env) " "
                           #_cljs.analyzer/*cljs-file*))))))
    (assoc m test expr)))

(defn- const? [env x]
  (let [m (and (list? x)
               ;; TODO
               #_(ana/resolve-var env (last x)))]
    (when m (get m :const))))

(defn core-case
  "Takes an expression, and a set of clauses.
  Each clause can take the form of either:
  test-constant result-expr
  (test-constant1 ... test-constantN)  result-expr
  The test-constants are not evaluated. They must be compile-time
  literals, and need not be quoted.  If the expression is equal to a
  test-constant, the corresponding result-expr is returned. A single
  default expression can follow the clauses, and its value will be
  returned if no clause matches. If no default expression is provided
  and no clause matches, an Error is thrown.
  Unlike cond and condp, case does a constant-time dispatch, the
  clauses are not considered sequentially.  All manner of constant
  expressions are acceptable in case, including numbers, strings,
  symbols, keywords, and (ClojureScript) composites thereof. Note that since
  lists are used to group multiple constants that map to the same
  expression, a vector can be used to match a list if needed. The
  test-constants need not be all of the same type."
  [_ &env e & clauses]
  (let [esym    (gensym)
        default (if (odd? (count clauses))
                  (last clauses)
                  `(throw
                    (js/Error.
                     (cljs.core/str "No matching clause: " ~esym))))
        env     &env
        pairs   (reduce
                 (fn [m [test expr]]
                   (cond
                     (seq? test)
                     (reduce
                      (fn [m test]
                        (let [test (if (symbol? test)
                                     (list 'quote test)
                                     test)]
                          (assoc-test m test expr env)))
                      m test)
                     (symbol? test)
                     (assoc-test m (list 'quote test) expr env)
                     :else
                     (assoc-test m test expr env)))
                 {} (partition 2 clauses))
        tests   (keys pairs)]
    (cond
      (every? (some-fn keyword? number? string? #?(:clj char? :cljs (fnil char? :nonchar)) #(const? env %)) tests)
      (let [no-default (if (odd? (count clauses)) (butlast clauses) clauses)
            tests      (mapv #(if (seq? %) (vec %) [%]) (take-nth 2 no-default))
            thens      (vec (take-nth 2 (drop 1 no-default)))]
        `(let [~esym ~e] (case* ~esym ~tests ~thens ~default)))
      #_#_(every? keyword? tests)
      (let [no-default (if (odd? (count clauses)) (butlast clauses) clauses)
            kw-str #(.substring (str %) 1)
            tests (mapv #(if (seq? %) (mapv kw-str %) [(kw-str %)]) (take-nth 2 no-default))
            thens (vec (take-nth 2 (drop 1 no-default)))]
        `(let [~esym ~e
               ~esym (if (keyword? ~esym) (subs (str ~esym) 1) nil)]
           (case* ~esym ~tests ~thens ~default)))

      ;; equality
      :else
      `(let [~esym ~e]
         (cond
           ~@(mapcat (fn [[m c]] `((cljs.core/= ~m ~esym) ~c)) pairs)
           :else ~default)))))

(defn core-dotdot
  "form => fieldName-symbol or (instanceMethodName-symbol args*)
     Expands into a member access (.) of the first member on the first
     argument, followed by the next member on the result, etc. For
     instance:
     (.. System (getProperties) (get \"os.name\"))
     expands to:
     (. (. System (getProperties)) (get \"os.name\"))
     but is easier to write, read, and understand."
  ([_ _ x form] `(. ~x ~form))
  ([_ _ x form & more] `(.. (. ~x ~form) ~@more)))

(defn ^:private js-this []
  (list 'js* "this"))

(defn core-this-as
  "Defines a scope where JavaScript's implicit \"this\" is bound to the name provided."
  [_ _ name & body]
  `(let [~name ~(js-this)]
     ~@body))

(defn core-unchecked-get
  "INTERNAL. Compiles to JavaScript property access using bracket notation. Does
  not distinguish between object and array types and not subject to compiler
  static analysis."
  [_ _ obj key]
  (list 'js* "(~{}[~{}])" obj key))

(defn core-unchecked-set
  "INTERNAL. Compiles to JavaScript property access using bracket notation. Does
  not distinguish between object and array types and not subject to compiler
  static analysis."
  [_ _ obj key val]
  (list 'js* "(~{}[~{}] = ~{})" obj key val))

(defn core-instance? [_ _ c x]
  (bool-expr (if (clojure.core/symbol? c)
               (list 'js* "(~{} instanceof ~{})" x c)
               `(let [c# ~c x# ~x]
                  (~'js* "(~{} instanceof ~{})" x# c#)))))

(defn core-time
  "Evaluates expr and prints the time it took. Returns the value of expr."
  [_ _ expr]
  `(let [start# (system-time)
         ret# ~expr]
     (prn (cljs.core/str "Elapsed time: "
                         (.toFixed (- (system-time) start#) 6)
                         " msecs"))
     ret#))

(defn core-declare
  "No-op for now"
  [_ _ _expr])

(defn core-letfn
  "fnspec ==> (fname [params*] exprs) or (fname ([params*] exprs)+)
     Takes a vector of function specs and a body, and generates a set of
     bindings of functions to their names. All of the names are available
     in all of the definitions of the functions, as well as the body."
  {:forms '[(letfn [fnspecs*] exprs*)],
   :special-form true, :url nil}
  [_ _ fnspecs & body]
  `(letfn* ~(vec (interleave (map first fnspecs)
                             (map #(cons `fn (rest %)) fnspecs)))
           ~@body))
