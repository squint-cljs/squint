(ns cherry.internal.macros)

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
