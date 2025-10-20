;; Adapted from CLJS core.cljc. Original copyright notice:

;;   Copyright (c) Rich Hickey. All rights reserved.  The use and distribution
;;   terms for this software are covered by the Eclipse Public License
;;   1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in
;;   the file epl-v10.html at the root of this distribution.  By using this
;;   software in any fashion, you are agreeing to be bound by the terms of this
;;   license.  You must not remove this notice, or any other, from this
;;   software.

(ns squint.internal.macros
  (:refer-clojure :exclude [coercive-boolean coercive-= coercive-not= coercive-not
                            bit-not bit-and unsafe-bit-and bit-or int bit-xor
                            bit-and-not bit-clear bit-flip bit-test
                            bit-shift-left bit-shift-right bit-shift-right-zero-fill
                            unsigned-bit-shift-right bit-set undefined?
                            simple-benchmark delay not=])
  (:require [clojure.core :as cc]
            [clojure.string :as str]
            [squint.compiler-common :as-alias ana]
            [squint.compiler.utils :as cu]
            #?(:clj [squint.internal.defmacro :as core]))
  #?(:cljs (:require-macros [squint.internal.defmacro :as core])))

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
    `(let [n# ~n]
       (loop [~i 0]
         (when (< ~i n#)
           ~@body
           (recur (inc ~i)))))))

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

(defn core-condp
  [_&form _&env pred expr & clauses]
  (let [gpred (gensym "pred__")
        gexpr (gensym "expr__")
        emit (fn emit [pred expr args]
               (let [[[a b c :as clause] more]
                       (split-at (if (= :>> (second args)) 3 2) args)
                       n (count clause)]
                 (cond
                  (= 0 n) `(throw (IllegalArgumentException. (str "No matching clause: " ~expr)))
                  (= 1 n) a
                  (= 2 n) `(if (~pred ~a ~expr)
                             ~b
                             ~(emit pred expr more))
                  :else `(if-let [p# (~pred ~a ~expr)]
                           (~c p#)
                           ~(emit pred expr more)))))]
    `(let [~gpred ~pred
           ~gexpr ~expr]
       ~(emit gpred gexpr clauses))))

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
                 (list 'js* "yield ~{}" body)
                 (let [k (first exprs)
                       v (second exprs)
                       subform (step (nnext exprs))]
                   (cond
                     (= k :let) `(let ~v ~subform)
                     (= k :while)
                     ;; emit literal JS because `if` detects that
                     ;; it's an expr context and emits a ternary,
                     ;; but you can't break inside of a ternary
                     (list 'js*
                           "if (~{}) {\n~{}\n} else { break; }"
                           v subform)
                     (= k :when) `(when ~v
                                    ~subform)
                     (keyword? k) (err "Invalid 'for' keyword" k)
                     :else (list 'squint.impl/for-of [k v] subform)))))]
    (list 'lazy (list 'js* "function* () {\n~{}\n}"
                      (step (seq seq-exprs))))))

(defn core-doseq
  "Repeatedly executes body (presumably for side-effects) with
  bindings and filtering as provided by \"for\".  Does not retain
  the head of the sequence. Returns nil."
  [_ _ seq-exprs & body]
  #_(assert-args doseq
                 (vector? seq-exprs) "a vector for its binding"
                 (even? (count seq-exprs)) "an even number of forms in binding vector")
  (let [res (let [err (fn [& msg] (throw (ex-info (apply str msg) {})))
                  step (fn step [exprs]
                         (if-not exprs
                           `(do ~@body)
                           (let [k (first exprs)
                                 v (second exprs)
                                 subform (step (nnext exprs))]
                             (cond
                               (= k :let) `(let ~v ~subform)
                               (= k :while) `(if ~v
                                               ~(with-meta `(~'js* "~{}" ~subform)
                                                  {:context :expr})
                                               ~(with-meta `(~'js* "break;\n")
                                                  {:context :expr}))
                               (= k :when) `(when ~v
                                              ~subform)
                               (keyword? k) (err "Invalid 'doseq' keyword" k)
                               :else (list 'squint.impl/for-of [k v] subform)))))]
              (step (seq seq-exprs)))]
    res))

(defn core-defonce
  "defs name to have the root value of init iff the named var has no root value,
  else init is unevaluated"
  [_&form _&env x init]
  (let [qualified (if (namespace x)
                    x
                    x
                    ;; TODO:
                    #_(symbol (str (-> &env :ns :name)) (name x)))]
    `(squint.impl/defonce ~x ~init) #_(do (~'js* "var ~{}" ~x)
         (when-not (exists? ~qualified)
           (def ~x ~init)))))

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
  (bool-expr `(let [c# ~c x# ~x
                    ret# (~'js* "(~{} instanceof ~{})" x# c#)]
                ret#)))

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

(defn core-or
  "Evaluates exprs one at a time, from left to right. If a form
  returns a logical true value, or returns that value and doesn't
  evaluate any of the other expressions, otherwise it returns the
  value of the last expression. (or) returns nil."
  {:added "1.0"}
  ([_ _] nil)
  ([_ _ x] x)
  ([_ _ x & next]
   `(let [or# ~x]
      (if or# or# (or ~@next)))))

(core/defmacro core-and
  "Evaluates exprs one at a time, from left to right. If a form
  returns logical false (nil or false), and returns that value and
  doesn't evaluate any of the other expressions, otherwise it returns
  the value of the last expr. (and) returns true."
  ([] true)
  ([x] x)
  ([x & next]
   (let [emit (-> &env :utils :emit)
         emitted (emit x (assoc &env :context :expr))
         tag (or (:tag emitted)
                 (:tag (meta x)))
         x (with-meta (list 'js* emitted)
             {:tag tag})]
     (if (= 'boolean tag)
       (list 'js* "(~{} && ~{})"
             x
             `(and ~@next))
       `(let [and# ~x]
          (if and# (and ~@next) and#))))))

(defn core-assert
  "Evaluates expr and throws an exception if it does not evaluate to
  logical true."
  ([_ _ x]
   `(when-not ~x
      (throw (js/Error. ~(str "Assert failed: " (pr-str x))))))
  ([_ _ x message]
   `(when-not ~x
      (throw (js/Error.
              (cljs.core/str "Assert failed: " ~message "\n" ~(pr-str x)))))))

(defn coercive-=
  [_ _ x y]
  (bool-expr (list 'js* "(~{} == ~{})" x y)))

(defn coercive-not=
  [_ _ x y]
  (bool-expr (list 'js* "(~{} != ~{})" x y)))

(defn coercive-not
  [_ _ x]
  (bool-expr (list 'js* "(!~{})" x)))

(defn coercive-boolean
  [_ _ x]
  (bool-expr (list 'js* "~{}" x)))

(core/defmacro ^::ana/numeric bit-not [x]
  (core/list 'js* "(~ ~{})" x))

(core/defmacro ^::ana/numeric bit-and
  ([x y] (core/list 'js* "(~{} & ~{})" x y))
  ([x y & more] `(cc/bit-and (cc/bit-and ~x ~y) ~@more)))

;; internal do not use
(core/defmacro ^::ana/numeric unsafe-bit-and
  ([x y] (bool-expr (core/list 'js* "(~{} & ~{})" x y)))
  ([x y & more] `(cc/unsafe-bit-and (cc/unsafe-bit-and ~x ~y) ~@more)))

(core/defmacro ^::ana/numeric bit-or
  ([x y] (core/list 'js* "(~{} | ~{})" x y))
  ([x y & more] `(cc/bit-or (cc/bit-or ~x ~y) ~@more)))

(core/defmacro ^::ana/numeric int [x]
  `(cc/bit-or ~x 0))

(core/defmacro ^::ana/numeric bit-xor
  ([x y] (core/list 'js* "(~{} ^ ~{})" x y))
  ([x y & more] `(cc/bit-xor (cc/bit-xor ~x ~y) ~@more)))

(core/defmacro ^::ana/numeric bit-and-not
  ([x y] (core/list 'js* "(~{} & ~~{})" x y))
  ([x y & more] `(cc/bit-and-not (cc/bit-and-not ~x ~y) ~@more)))

(core/defmacro ^::ana/numeric bit-clear [x n]
  (core/list 'js* "(~{} & ~(1 << ~{}))" x n))

(core/defmacro ^::ana/numeric bit-flip [x n]
  (core/list 'js* "(~{} ^ (1 << ~{}))" x n))

(core/defmacro bit-test [x n]
  (bool-expr (core/list 'js* "((~{} & (1 << ~{})) != 0)" x n)))

(core/defmacro ^::ana/numeric bit-shift-left [x n]
  (core/list 'js* "(~{} << ~{})" x n))

(core/defmacro ^::ana/numeric bit-shift-right [x n]
  (core/list 'js* "(~{} >> ~{})" x n))

(core/defmacro ^::ana/numeric bit-shift-right-zero-fill [x n]
  (core/list 'js* "(~{} >>> ~{})" x n))

(core/defmacro ^::ana/numeric unsigned-bit-shift-right [x n]
  (core/list 'js* "(~{} >>> ~{})" x n))

(core/defmacro ^::ana/numeric bit-set [x n]
  (core/list 'js* "(~{} | (1 << ~{}))" x n))

(core/defmacro undefined?
  [x]
  (bool-expr (core/list 'js* "(void 0 === ~{})" x)))

(core/defmacro simple-benchmark
  [bindings expr iterations & {:keys [print-fn] :or {print-fn 'println}}]
  (let [bs-str   (pr-str bindings)
             expr-str (pr-str expr)]
    `(let ~bindings
       (let [start#   (.getTime (js/Date.))
             ret#     (dotimes [_# ~iterations] ~expr)
             end#     (.getTime (js/Date.))
             elapsed# (- end# start#)]
         (~print-fn (str ~bs-str ", " ~expr-str ", "
                         ~iterations " runs, " elapsed# " msecs"))))))

(core/defmacro delay
  "Takes a body of expressions and yields a Delay object that will
  invoke the body only the first time it is forced (with force or deref/@), and
  will cache the result and return it on all subsequent force
  calls."
  [& body]
  `(new cljs.core/Delay (fn [] ~@body) nil))

(defn constant? [x]
  (or (number? x)
      (keyword? x)
      (string? x)
      (boolean? x)))

(defn primitive? [tag]
  (contains? #{'number 'string 'boolean} tag))

(core/defmacro equals
  ([_] true)
  ([x y]
   (let [emit (-> &env :utils :emit)
         x-emitted (emit x (assoc &env :context :expr))
         x-tag (or (:tag x-emitted)
                   (:tag (meta x)))
         x (with-meta (list 'js* (str x-emitted))
             {:tag x-tag})
         y-emitted (emit y (assoc &env :context :expr))
         y-tag (or (:tag y-emitted)
                   (:tag (meta y)))
         y (with-meta (list 'js* (str y-emitted))
             {:tag y-tag})]
     (with-meta
       (if (or (primitive? x-tag) (primitive? y-tag))
         (core/list 'js* "(~{} === ~{})" x y)
         `(cljs.core/_EQ_ ~x ~y))
       {:tag 'boolean})))
  ([x y & xs]
   (list 'js* "(~{} && ~{})" `(cc/= ~x ~y) `(cc/= ~y ~@xs))))

(core/defmacro stringify [& xs]
  (let [emit (-> &env :utils :emit)
        args (keep (fn [expr]
                     (cond
                       (nil? expr) nil
                       (and (string? expr)
                            (re-matches #"[A-Za-z0-9_-]*" expr)) expr
                       :else
                       (let [emitted (emit expr (assoc &env :context :expr))
                             tag (or (:tag emitted)
                                     (:tag (meta expr)))
                             const? (constant? expr)]
                         (if (primitive? tag)
                           (if (or
                                ;; escape literal strings that may contain backticks, newlines etc
                                (and const? (= 'string tag))
                                (not const?))
                             (str "${" emitted "}")
                             emitted)
                           (str "${" emitted "??''}"))))) xs)]
    (with-meta `(~'js* ~(str "`" (str/join args) "`"))
      {:tag 'string})))

(core/defmacro assoc-inline [x & xs]
  (assert (even? (count xs)) "assoc! must be called with and object and an even amount of arguments")
  (let [emit (-> &env :utils :emit)
        emitted (emit x (assoc &env :context :expr))
        tag (or (:tag emitted)
                (:tag (meta x)))
        x (with-meta (list 'js* (str emitted))
            {:tag tag})]
    (if (= 'object tag)
      (with-meta
        (list* 'js* (str "({...~{},"
                         (str/join ","
                                   (repeat (/ (count xs) 2) "~{}:~{}"))
                         "})")
               x xs)
        {:tag 'object})
      (let [[fn _ & tail] &form]
        (with-meta
          (list* fn x tail)
          (assoc (meta &form)
                 :squint.compiler/skip-macro true))))))

(core/defmacro assoc!-inline [x & xs]
  (assert (even? (count xs)) "assoc! must be called with and object and an even amount of arguments")
  (let [emit (-> &env :utils :emit)
        emitted (emit x (assoc &env :context :expr))
        tag (or (:tag emitted)
                (:tag (meta x)))
        x* x
        x (with-meta (list 'js* (str emitted))
            {:tag tag})]
    (if (= 'object tag)
      (if-not (symbol? x*)
        (let [obj-sym (with-meta (gensym)
                        {:tag tag})]
          (with-meta `(^:=> (fn [~obj-sym]
                         (assoc! ~obj-sym ~@xs)) ~x)
            ;; TODO: we shouldn't have to add a tag here with function return
            ;; tag inference, which isn't yet available, but within reach
            {:tag tag}))
        (with-meta
          (list* 'js* (str "("
                           (str/join "," (repeat (/ (count xs) 2) "~{}"))
                           ",~{}"
                           ")")
                 (concat
                  (map (fn [[k v]]
                         `(aset ~x ~k ~v))
                       (partition 2 xs))
                  [x]))
          {:tag 'object}))
      (let [[fn _ & tail] &form]
        (with-meta
          (list* fn x tail)
          (assoc (meta &form)
                 :squint.compiler/skip-macro true))))))

(core/defmacro get-inline
  ([x b]
   (let [emit (-> &env :utils :emit)
         emitted (emit x (assoc &env :context :expr))
         tag (or (:tag emitted)
                 (:tag (meta x)))
         x (with-meta (list 'js* (str emitted))
             {:tag tag})]
     (if (= 'object tag)
       `(cljs.core/aget ~x ~b)
       (let [[fn _ & tail] &form]
         (with-meta
           (list* fn x tail)
           (assoc (meta &form)
                  :squint.compiler/skip-macro true))))))
  ([x b not-found]
   (let [emit (-> &env :utils :emit)
         emitted (emit x (assoc &env :context :expr))
         tag (or (:tag emitted)
                 (:tag (meta x)))
         x* x
         x (with-meta (list 'js* (str emitted))
             {:tag tag})]
     (if (= 'object tag)
       (if (and (symbol? x*)
                (or (constant? b)
                    (symbol? b)))
         (list 'js* "(~{} in ~{} ? ~{} : ~{})"
               b
               x
               `(cljs.core/aget ~x ~b)
               not-found)
         (let [obj-sym (with-meta (gensym)
                         {:tag tag})
               key-sym (gensym)]
           `(^:=> (fn [~obj-sym ~key-sym]
                    ~(list 'js* "(~{} in ~{} ? ~{} : ~{})"
                           key-sym
                           obj-sym
                           `(cljs.core/aget ~obj-sym ~key-sym)
                           not-found)) ~x ~b)))
       (let [[fn _ & tail] &form]
         (with-meta
           (list* fn x tail)
           (assoc (meta &form)
                  :squint.compiler/skip-macro true)))))))

(core/defmacro not=
  [& xs]
  `(not (= ~@xs)))
