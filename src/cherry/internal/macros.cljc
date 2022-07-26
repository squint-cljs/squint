(ns cherry.internal.macros
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
  `(new cljs.core/LazySeq nil (fn [] ~@body) nil nil))

(defn core-for
  "List comprehension. Takes a vector of one or more
   binding-form/collection-expr pairs, each followed by zero or more
   modifiers, and yields a lazy sequence of evaluations of expr.
   Collections are iterated in a nested fashion, rightmost fastest,
   and nested coll-exprs can refer to bindings created in prior
   binding-forms.  Supported modifiers are: :let [binding-form expr ...],
   :while test, :when test.
  (take 100 (for [x (range 100000000) y (range 1000000) :while (< y x)]  [x y]))"
  [_ _ seq-exprs body-expr]
  #_(assert-args for
    (vector? seq-exprs) "a vector for its binding"
    (even? (count seq-exprs)) "an even number of forms in binding vector")
  (let [to-groups (fn [seq-exprs]
                         (reduce (fn [groups [k v]]
                                   (if (keyword? k)
                                     (conj (pop groups) (conj (peek groups) [k v]))
                                     (conj groups [k v])))
                           [] (partition 2 seq-exprs)))
             err (fn [& msg] (throw (ex-info (apply str msg) {})))
             emit-bind (fn emit-bind [[[bind expr & mod-pairs]
                                       & [[_ next-expr] :as next-groups]]]
                         (let [giter (gensym "iter__")
                                    gxs (gensym "s__")
                                    do-mod (fn do-mod [[[k v :as pair] & etc]]
                                             (cond
                                               (= k :let) `(let ~v ~(do-mod etc))
                                               (= k :while) `(when ~v ~(do-mod etc))
                                               (= k :when) `(if ~v
                                                              ~(do-mod etc)
                                                              (recur (rest ~gxs)))
                                               (keyword? k) (err "Invalid 'for' keyword " k)
                                               next-groups
                                               `(let [iterys# ~(emit-bind next-groups)
                                                      fs# (seq (iterys# ~next-expr))]
                                                  (if fs#
                                                    (concat fs# (~giter (rest ~gxs)))
                                                    (recur (rest ~gxs))))
                                               :else `(cons ~body-expr
                                                        (~giter (rest ~gxs)))))]
                           (if next-groups
                             #_ "not the inner-most loop"
                             `(fn ~giter [~gxs]
                                (lazy-seq
                                  (loop [~gxs ~gxs]
                                    (when-first [~bind ~gxs]
                                      ~(do-mod mod-pairs)))))
                             #_"inner-most loop"
                             (let [gi (gensym "i__")
                                        gb (gensym "b__")
                                        do-cmod (fn do-cmod [[[k v :as pair] & etc]]
                                                  (cond
                                                    (= k :let) `(let ~v ~(do-cmod etc))
                                                    (= k :while) `(when ~v ~(do-cmod etc))
                                                    (= k :when) `(if ~v
                                                                   ~(do-cmod etc)
                                                                   (recur
                                                                     (unchecked-inc ~gi)))
                                                    (keyword? k)
                                                    (err "Invalid 'for' keyword " k)
                                                    :else
                                                    `(do (chunk-append ~gb ~body-expr)
                                                         (recur (unchecked-inc ~gi)))))]
                               `(fn ~giter [~gxs]
                                  (lazy-seq
                                    (loop [~gxs ~gxs]
                                      (when-let [~gxs (seq ~gxs)]
                                        (if (chunked-seq? ~gxs)
                                          (let [c# ^not-native (chunk-first ~gxs)
                                                size# (count c#)
                                                ~gb (chunk-buffer size#)]
                                            (if (coercive-boolean
                                                  (loop [~gi 0]
                                                    (if (< ~gi size#)
                                                      (let [~bind (-nth c# ~gi)]
                                                        ~(do-cmod mod-pairs))
                                                      true)))
                                              (chunk-cons
                                                (chunk ~gb)
                                                (~giter (chunk-rest ~gxs)))
                                              (chunk-cons (chunk ~gb) nil)))
                                          (let [~bind (first ~gxs)]
                                            ~(do-mod mod-pairs)))))))))))]
    `(let [iter# ~(emit-bind (to-groups seq-exprs))]
       (iter# ~(second seq-exprs)))))

(defn core-doseq
  "Repeatedly executes body (presumably for side-effects) with
  bindings and filtering as provided by \"for\".  Does not retain
  the head of the sequence. Returns nil."
  [_ _ seq-exprs & body]
  #_(assert-args doseq
    (vector? seq-exprs) "a vector for its binding"
    (even? (count seq-exprs)) "an even number of forms in binding vector")
  (let [err (fn [& msg] (throw (ex-info (apply str msg) {})))
             step (fn step [recform exprs]
                    (if-not exprs
                      [true `(do ~@body nil)]
                      (let [k (first exprs)
                                 v (second exprs)

                                 seqsym (gensym "seq__")
                                 recform (if (keyword? k) recform `(recur (next ~seqsym) nil 0 0))
                                 steppair (step recform (nnext exprs))
                                 needrec (steppair 0)
                                 subform (steppair 1)]
                        (cond
                          (= k :let) [needrec `(let ~v ~subform)]
                          (= k :while) [false `(when ~v
                                                 ~subform
                                                 ~@(when needrec [recform]))]
                          (= k :when) [false `(if ~v
                                                (do
                                                  ~subform
                                                  ~@(when needrec [recform]))
                                                ~recform)]
                          (keyword? k) (err "Invalid 'doseq' keyword" k)
                          :else (let [chunksym (with-meta (gensym "chunk__")
                                                      {:tag 'not-native})
                                           countsym (gensym "count__")
                                           isym     (gensym "i__")
                                           recform-chunk  `(recur ~seqsym ~chunksym ~countsym (unchecked-inc ~isym))
                                           steppair-chunk (step recform-chunk (nnext exprs))
                                           subform-chunk  (steppair-chunk 1)]
                                  [true `(loop [~seqsym   (seq ~v)
                                                ~chunksym nil
                                                ~countsym 0
                                                ~isym     0]
                                           (if (< ~isym ~countsym)
                                             (let [~k (-nth ~chunksym ~isym)]
                                               ~subform-chunk
                                               ~@(when needrec [recform-chunk]))
                                             (when-let [~seqsym (seq ~seqsym)]
                                               (if (chunked-seq? ~seqsym)
                                                 (let [c# (chunk-first ~seqsym)]
                                                   (recur (chunk-rest ~seqsym) c#
                                                     (count c#) 0))
                                                 (let [~k (first ~seqsym)]
                                                   ~subform
                                                   ~@(when needrec [recform]))))))])))))]
    (nth (step nil (seq seq-exprs)) 1)))

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
     #?(:clj (clojure.IllegalArgumentException.
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
                            (cljs.str "No matching clause: " ~esym))))
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
      (every? (some-fn number? string? #?(:clj char? :cljs (fnil char? :nonchar)) #(const? env %)) tests)
      (let [no-default (if (odd? (count clauses)) (butlast clauses) clauses)
                 tests      (mapv #(if (seq? %) (vec %) [%]) (take-nth 2 no-default))
                 thens      (vec (take-nth 2 (drop 1 no-default)))]
        `(let [~esym ~e] (case* ~esym ~tests ~thens ~default)))

      (every? keyword? tests)
      (let [no-default (if (odd? (count clauses)) (butlast clauses) clauses)
                 kw-str #(.substring (str %) 1)
                 tests (mapv #(if (seq? %) (mapv kw-str %) [(kw-str %)]) (take-nth 2 no-default))
                 thens (vec (take-nth 2 (drop 1 no-default)))]
        `(let [~esym ~e
               ~esym (if (keyword? ~esym) (.-fqn ~(vary-meta esym assoc :tag 'cljs.Keyword)) nil)]
           (case* ~esym ~tests ~thens ~default)))

      ;; equality
      :else
      `(let [~esym ~e]
         (cond
           ~@(mapcat (fn [[m c]] `((cljs.core/= ~m ~esym) ~c)) pairs)
           :else ~default)))))
