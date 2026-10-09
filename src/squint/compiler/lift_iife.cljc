(ns squint.compiler.lift-iife
  "Replaces an IIFE in a function body with its statements if its statement
  evaluates the IIFE first."
  (:require [clojure.string :as str]
            [squint.compiler.js-ast :as ast]))

(defn- return? [x]
  (= :return-statement (:type (cond-> x (ast/code? x) :js))))

(defn- statement-wrapper?
  "Returns true if x is a :raw node that terminates a statement."
  [x]
  (and (= :raw (:type x)) (::ast/terminate x)))

(defn- statement-node? [x]
  (or (contains? #{:variable-declaration :expression-statement :if-statement :return-statement}
                 (:type x))
      (statement-wrapper? x)))

(defn- flatten-statements
  "Returns the statements of an IIFE body in order, or nil if a part is not
  a complete statement."
  [x]
  (cond
    (nil? x) []
    (string? x) (cond (str/blank? x) []
                      (re-find #";\n$" x) [x]
                      :else nil)
    (ast/code? x) (flatten-statements (:js x))
    (statement-wrapper? x)
    (let [inner (first (:parts x))
          inner (cond-> inner (ast/code? inner) :js)
          items (when (and (= :raw (:type inner)) (not (statement-wrapper? inner)))
                  (flatten-statements inner))]
      (if (seq items)
        (conj (pop items) (ast/terminate (peek items)))
        [x]))
    (statement-node? x) [x]
    (= :raw (:type x)) (flatten-statements (:parts x))
    (sequential? x) (reduce (fn [acc y]
                              (if-let [ys (flatten-statements y)]
                                (into acc ys)
                                (reduced nil)))
                            [] x)))

(defn- final-return
  "Returns a vector of the argument of statement x, or nil if x does not return."
  [x]
  (cond (return? x) [(:argument (cond-> x (ast/code? x) :js))]
        (statement-wrapper? x) (final-return (first (:parts x)))))

(defn- simple-iife? [x]
  (let [callee (:callee x)]
    (and (= :call-expression (:type x))
         (::ast/iife callee)
         (not (:async callee))
         (not (:generator callee))
         (empty? (:arguments x)))))

(defn- pure? [x]
  (and (string? x)
       (boolean (re-matches #"[A-Za-z_$][\w$.]*|-?\d+(\.\d+)?|\"[^\"\\]*\"" x))))

(defn- function? [x]
  (contains? #{:function-expression :arrow-function-expression} (:type x)))

(defn- contains-return?
  "Returns true if x returns outside a nested function."
  [x]
  (cond (string? x) (boolean (re-find #"\breturn\b" x))
        (return? x) true
        (function? x) false
        (ast/code? x) (contains-return? (:js x))
        (ast/node? x) (some contains-return? (vals (dissoc x :type :loc)))
        (sequential? x) (some contains-return? x)
        :else false))

(defn- tail-returns?
  "Returns true if every return in statement x is in tail position."
  [x]
  (cond (return? x) true
        (statement-wrapper? x) (tail-returns? (first (:parts x)))
        (= :if-statement (:type x)) (and (not (contains-return? (:test x)))
                                         (tail-returns? (:consequent x))
                                         (or (nil? (:alternate x))
                                             (tail-returns? (:alternate x))))
        (= :block-statement (:type x)) (if-let [stmts (flatten-statements (:body x))]
                                         (or (empty? stmts)
                                             (and (not-any? contains-return? (pop stmts))
                                                  (tail-returns? (peek stmts))))
                                         false)
        :else (not (contains-return? x))))

(defn- returns->assignments
  "Returns x with each return outside a nested function replaced by an
  assignment to tmp."
  [x tmp]
  (cond (ast/code? x) (update x :js returns->assignments tmp)
        (return? x) (ast/node {:type :assignment-expression :operator "=" :left tmp
                               :right (let [a (:argument x)] (if (nil? a) "null" a))})
        (function? x) x
        (ast/node? x) (reduce-kv (fn [m k v]
                                   (if (or (= :type k) (= :loc k) (namespace k))
                                     m
                                     (assoc m k (returns->assignments v tmp))))
                                 x x)
        (sequential? x) (mapv #(returns->assignments % tmp) x)
        :else x))

(def ^:private ^:dynamic *tmp-counter* nil)

(defn- fresh-tmp []
  (str "squint$iife$" (vswap! *tmp-counter* inc)))

(declare lift-expr)

(def ^:private call-operand-types
  #{:call-expression :new-expression :member-expression
    :parenthesized-expression :array-expression})

(defn- lift-operand
  "Returns [statements expr'] with expr' parenthesized unless it is pure or
  its type is in safe-types."
  ([x] (lift-operand x call-operand-types))
  ([x safe-types]
   (let [[stmts x'] (lift-expr x)]
     [stmts (if (and (seq stmts)
                     (not (pure? x'))
                     (not (contains? safe-types (:type x'))))
              (ast/node {:type :parenthesized-expression :expression x'})
              x')])))

(defn- lift-args
  "Returns [statements args'] lifted from the first impure argument."
  [args]
  (loop [i 0]
    (if (< i (count args))
      (let [arg (nth args i)]
        (if (pure? arg)
          (recur (inc i))
          (let [[stmts arg'] (lift-expr arg)]
            [stmts (assoc args i arg')])))
      [[] args])))

(defn- lift-expr
  "Returns [statements expr'] where statements run before expr'."
  [x]
  (cond
    (ast/code? x) (let [[stmts js] (lift-expr (:js x))]
                    [stmts (assoc x :js js)])
    (simple-iife? x)
    (let [stmts (flatten-statements (:body (:body (:callee x))))
          lst (peek stmts)
          [ret] (when (seq stmts) (final-return lst))]
      (cond
        (or (empty? stmts) (some contains-return? (pop stmts))) [[] x]
        ret (let [[more ret'] (lift-expr ret)]
              [(into (pop stmts) more) ret'])
        (and (= :if-statement (:type (cond-> lst (statement-wrapper? lst) (-> :parts first))))
             (tail-returns? lst))
        (let [tmp (fresh-tmp)]
          [(conj (pop stmts) (str "let " tmp ";\n") (returns->assignments lst tmp)) tmp])
        :else [[] x]))
    (= :parenthesized-expression (:type x))
    (let [[stmts e] (lift-expr (:expression x))]
      [stmts (assoc x :expression e)])
    (contains? #{:await-expression} (:type x))
    (let [[stmts e] (lift-operand (:argument x))]
      [stmts (assoc x :argument e)])
    (= :conditional-expression (:type x))
    (let [[stmts e] (lift-expr (:test x))]
      [stmts (assoc x :test e)])
    (contains? #{:call-expression :new-expression} (:type x))
    (if (pure? (:callee x))
      (let [[stmts args] (lift-args (vec (:arguments x)))]
        [stmts (assoc x :arguments args)])
      (let [[stmts callee] (lift-operand (:callee x)
                                         (if (= :new-expression (:type x))
                                           #{:parenthesized-expression}
                                           call-operand-types))]
        [stmts (assoc x :callee callee)]))
    :else [[] x]))

(defn- lift-statement
  "Returns statement x preceded by the statements lifted out of it."
  [x]
  (let [[stmts x']
        (case (:type x)
          :variable-declaration
          (if (= 1 (count (:declarations x)))
            (let [d (first (:declarations x))
                  [stmts init] (lift-expr (:init d))]
              [stmts (assoc x :declarations [(assoc d :init init)])])
            [[] x])
          :return-statement
          (let [[stmts arg] (lift-expr (:argument x))]
            [stmts (assoc x :argument arg)])
          :expression-statement
          (let [[stmts e] (lift-operand (:expression x))]
            [stmts (assoc x :expression e)])
          :if-statement
          (let [[stmts e] (lift-expr (:test x))]
            [stmts (assoc x :test e)])
          :raw
          (let [[e] (:parts x)
                e (cond-> e (ast/code? e) :js)
                [stmts e'] (if (return? e)
                             (let [[stmts arg] (lift-expr (:argument e))]
                               [stmts (assoc e :argument arg)])
                             (lift-operand e))]
            [stmts (assoc x :parts [e'])])
          [[] x])]
    (if (seq stmts) (conj stmts x') x)))

(declare rewrite)

(defn- rewrite-node [n in-fn]
  (let [in-fn (or in-fn (contains? #{:function-expression :arrow-function-expression}
                                   (:type n)))]
    (reduce-kv (fn [m k v]
                 (if (or (= :type k) (= :loc k) (namespace k))
                   m
                   (assoc m k (rewrite v in-fn))))
               n n)))

(defn- rewrite
  "Returns x with IIFEs lifted out of statements inside function bodies."
  [x in-fn]
  (cond
    (ast/node? x) (let [x (rewrite-node x in-fn)]
                    (if (and in-fn (statement-node? x))
                      (lift-statement x)
                      x))
    (ast/code? x) (assoc x :js (rewrite (:js x) in-fn))
    (vector? x) (mapv #(rewrite % in-fn) x)
    (seq? x) (mapv #(rewrite % in-fn) x)
    :else x))

(defn lift
  "Returns program node x with IIFEs in function bodies replaced by their
  statements if that keeps evaluation order."
  [x]
  (binding [*tmp-counter* (volatile! 0)]
    (rewrite x false)))
