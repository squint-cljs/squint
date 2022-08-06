;; Adapted from Scriptjure. Original copyright notice:

;; Copyright (c) Allen Rohner, 2009. All rights reserved.  The use
;; and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this
;; distribution.  By using this software in any fashion, you are
;; agreeing to be bound by the terms of this license.  You must not
;; remove this notice, or any other, from this software.

(ns cherry.compiler
  (:require
   #?(:cljs ["fs" :as fs])
   #?(:cljs [goog.string.format])
   #?(:cljs [goog.string :as gstring])
   #?(:clj [cherry.resource :as resource])
   [cherry.internal.deftype :as deftype]
   [cherry.internal.destructure :refer [core-let]]
   [cherry.internal.fn :refer [core-defmacro core-defn core-fn]]
   [cherry.internal.loop :as loop]
   [cherry.internal.macros :as macros]
   [cherry.internal.protocols :as protocols]
   [clojure.string :as str]
   [com.reasonr.string :as rstr]
   [edamame.core :as e])
  #?(:cljs (:require-macros [cherry.resource :as resource])))

#?(:cljs (def Exception js/Error))

#?(:cljs (def format gstring/format))

(defmulti emit (fn [expr _env] (type expr)))

(defmulti emit-special (fn [disp _env & _args] disp))

(defmethod emit-special 'js* [_ env [_js* template & substitutions]]
  (reduce (fn [template substitution]
            (str/replace-first template "~{}" (emit substitution env)))
          template
          substitutions))

(defn emit-wrap [env s]
  ;; (prn :wrap s (:contet env))
  (if (= :return (:context env))
    (format "return %s;" s)
    s))

(defn expr-env [env]
  (assoc env :context :expr))

(defmethod emit-special 'throw [_ env [_ expr]]
  (str "throw " (emit expr (expr-env env))))

(def statement-separator ";\n")

;; TODO: move to context argument
(def ^:dynamic *async* false)
(def ^:dynamic *imported-core-vars* (atom #{}))
(def ^:dynamic *public-vars* (atom #{}))

(defn statement [expr]
  (if (not (= statement-separator (rstr/tail (count statement-separator) expr)))
    (str expr statement-separator)
    expr))

(defn comma-list [coll]
  (str "(" (str/join ", " coll) ")"))

(defmethod emit nil [_ env]
  (emit-wrap env "null"))

#?(:clj (derive #?(:clj java.lang.Integer) ::number))
#?(:clj (derive #?(:clj java.lang.Long) ::number))
#?(:cljs (derive js/Number ::number))

(defmethod emit ::number [expr env]
  (->> (str expr)
       (emit-wrap env)))

(defmethod emit #?(:clj java.lang.String :cljs js/String) [^String expr env]
  (emit-wrap env (pr-str expr)))

(defmethod emit #?(:clj clojure.lang.Keyword :cljs Keyword) [expr env]
  (swap! *imported-core-vars* conj 'keyword)
  (emit-wrap env (str (format "keyword(%s)" (pr-str (subs (str expr) 1))))))

(defn munge* [expr]
  (let [munged (str (munge expr))
        keep #{"import" "await"}]
    (cond-> munged
      (and (str/ends-with? munged "$")
           (contains? keep (str expr)))
      (str/replace #"\$$" ""))))

(declare core-vars)

(defn maybe-core-var [sym]
  (if (contains? core-vars sym)
    (let [sym (symbol (munge* sym))
          ]
      (swap! *imported-core-vars* conj sym)
      sym)
    sym))

(defmethod emit #?(:clj clojure.lang.Symbol :cljs Symbol) [expr env]
  (if (:quote env)
    (emit-wrap env
               (emit (list 'cljs.core/symbol
                           (str expr))
                     (dissoc env :quote)))
    (let [expr (if-let [sym-ns (namespace expr)]
                 (or (when (or (= "cljs.core" sym-ns)
                               (= "clojure.core" sym-ns))
                       (maybe-core-var (symbol (name expr))))
                     (when (= "js" sym-ns)
                       (symbol (name expr)))
                     expr)
                 (maybe-core-var expr))
          expr-ns (namespace expr)
          expr (if-let [renamed (get (:var->ident env) expr)]
                 (str renamed)
                 (str expr-ns (when expr-ns
                                ".")
                      (munge* (name expr))))]
      (emit-wrap env (str expr)))))

#?(:clj (defmethod emit #?(:clj java.util.regex.Pattern) [expr _env]
          (str \/ expr \/)))

(defmethod emit :default [expr env]
  ;; RegExp case moved here:
  ;; References to the global RegExp object prevents optimization of regular expressions.
  (emit-wrap env (str expr)))

(def special-forms (set ['var '. 'if 'funcall 'fn 'fn* 'quote 'set!
                         'return 'delete 'new 'do 'aget 'while
                         'inc! 'dec! 'dec 'inc 'defined? 'and 'or
                         '? 'try 'break 'throw
                         'js/await 'const 'let 'let* 'ns 'def 'loop*
                         'recur 'js* 'case* 'deftype*]))

(def built-in-macros {'-> macros/core->
                      '->> macros/core->>
                      'as-> macros/core-as->
                      'comment macros/core-comment
                      'dotimes macros/core-dotimes
                      'if-not macros/core-if-not
                      'when macros/core-when
                      'when-not macros/core-when-not
                      'doto macros/core-doto
                      'cond macros/core-cond
                      'cond-> macros/core-cond->
                      'cond->> macros/core-cond->>
                      'if-let macros/core-if-let
                      'if-some macros/core-if-some
                      'when-let macros/core-when-let
                      'when-first macros/core-when-first
                      'when-some macros/core-when-some
                      'some-> macros/core-some->
                      'some>> macros/core-some->>
                      'loop loop/core-loop
                      'doseq macros/core-doseq
                      'for macros/core-for
                      'lazy-seq macros/core-lazy-seq
                      'defonce macros/core-defonce
                      'exists? macros/core-exists?
                      'case macros/core-case
                      '.. macros/core-dotdot
                      'defmacro core-defmacro
                      'this-as macros/core-this-as
                      'unchecked-get macros/core-unchecked-get
                      'unchecked-set macros/core-unchecked-set
                      'defprotocol protocols/core-defprotocol
                      'extend-type protocols/core-extend-type
                      'deftype deftype/core-deftype
                      'defn core-defn
                      'defn- core-defn})

(def core-config (resource/edn-resource "cherry/cljs.core.edn"))

(def core-vars (conj (:vars core-config) 'goog_typeOf))

(def prefix-unary-operators (set ['!]))

(def suffix-unary-operators (set ['++ '--]))

(def infix-operators (set ['+ '+= '- '-= '/ '* '% '== '=== '< '> '<= '>= '!=
                           '<< '>> '<<< '>>> '!== '& '| '&& '|| 'not= 'instanceof]))

(def chainable-infix-operators (set ['+ '- '* '/ '& '| '&& '||]))


(defn special-form? [expr]
  (contains? special-forms expr))

(defn infix-operator? [expr]
  (contains? infix-operators expr))

(defn prefix-unary? [expr]
  (contains? prefix-unary-operators expr))

(defn suffix-unary? [expr]
  (contains? suffix-unary-operators expr))

(defn emit-prefix-unary [_type [operator arg]]
  (str operator (emit arg)))

(defn emit-suffix-unary [_type [operator arg]]
  (str (emit arg) operator))

(defn emit-args [env args]
  (let [env (assoc env :context :expr)]
    (map #(emit % env) args)))

(defn emit-infix [_type enc-env [operator & args]]
  (let [env (assoc enc-env :context :expr)]
    (when (and (not (chainable-infix-operators operator)) (> (count args) 2))
      (throw (Exception. (str "operator " operator " supports only 2 arguments"))))
    (->> (let [substitutions {'== '=== '!= '!== 'not= '!==}]
           (str "(" (str/join (str " " (or (substitutions operator) operator) " ")
                              (emit-args env args)) ")"))
         (emit-wrap enc-env))))

(def ^{:dynamic true} var-declarations nil)

#_(defmethod emit-special 'var [type env [var & more]]
    (apply swap! var-declarations conj (filter identity (map (fn [name i] (when (odd? i) name)) more (iterate inc 1))))
    (apply str (interleave (map (fn [[name expr]]
                                  (str (when-not var-declarations "var ") (emit env name) " = " (emit env expr)))
                                (partition 2 more))
                           (repeat statement-separator))))

(def ^:dynamic *recur-targets* [])

(declare emit-do wrap-iife)

(defmethod emit-special 'quote [_ env [_ form]]
  (emit-wrap env (emit form (expr-env (assoc env :quote true)))))

(defn emit-let [enc-env bindings body is-loop]
  (let [context (:context enc-env)
        env (assoc enc-env :context :expr)
        partitioned (partition 2 bindings)
        iife? (= :expr context)
        upper-var->ident (:var->ident enc-env)
        [bindings var->ident]
        (reduce (fn [[acc var->ident] [var-name rhs]]
                  (let [vm (meta var-name)
                        rename? (not (:cherry.compiler/no-rename vm))
                        renamed (if rename? (munge (gensym var-name))
                                    var-name)
                        lhs (str renamed)
                        rhs (emit rhs (assoc env :var->ident var->ident))
                        expr (format "let %s = %s;\n" lhs rhs)
                        var->ident (assoc var->ident var-name renamed)]
                    [(str acc expr) var->ident]))
                ["" upper-var->ident]
                partitioned)
        enc-env (assoc enc-env :var->ident var->ident)]
    (cond->> (str
              bindings
              (when is-loop
                (str "while(true){\n"))
              ;; TODO: move this to env arg?
              (binding [*recur-targets*
                        (if is-loop (map var->ident (map first partitioned))
                            *recur-targets*)]
                (emit-do (if iife?
                           (assoc enc-env :context :return)
                           enc-env) body))
              (when is-loop
                ;; TODO: not sure why I had to insert the ; here, but else
                ;; (loop [x 1] (+ 1 2 x)) breaks
                (str ";break;\n}\n")))
      (= :expr context)
      (wrap-iife))))

(defmethod emit-special 'let* [_type enc-env [_let bindings & body]]
  (emit-let enc-env bindings body false))

(defmethod emit-special 'deftype* [_ env [_ t fields pmasks body]]
  (let [fields (map munge fields)]
    (str "var " (munge t) " = " (format "function %s {
%s
%s
%s
}"
                                        (comma-list fields)
                                        (str/join "\n"
                                                  (map (fn [fld]
                                                         (str "this." fld " = " fld ";"))
                                                       fields))
                                        (str/join "\n"
                                                  (map (fn [[pno pmask]]
                                                         (str "this.cljs$lang$protocol_mask$partition" pno "$ = " pmask ";"))
                                                       pmasks))
                                        (emit body
                                              (->
                                               env
                                               (update
                                                :var->ident
                                                (fn [vi]
                                                  (merge
                                                   vi
                                                   (zipmap fields
                                                           (map (fn [fld]
                                                                  (symbol (str "self__." fld)))
                                                                fields)))))
                                               (assoc :type true)))))))


#_(defmethod emit* :deftype
    [{:keys [t fields pmasks body protocols]}]
    (let [fields (map munge fields)]
      (emitln "")
      (emitln "/**")
      (emitln "* @constructor")
      (doseq [protocol protocols]
        (emitln " * @implements {" (munge (str protocol)) "}"))
      (emitln "*/")
      (emitln (munge t) " = (function (" (comma-sep fields) "){")
      (doseq [fld fields]
        (emitln "this." fld " = " fld ";"))
      (doseq [[pno pmask] pmasks]
        (emitln "this.cljs$lang$protocol_mask$partition" pno "$ = " pmask ";"))
      (emitln "});")
      (emit body)))

(defmethod emit-special 'loop* [_ env [_ bindings & body]]
  (emit-let env bindings body true))

(defmethod emit-special 'case* [_ env [_ v tests thens default]]
  (let [expr? (= :expr (:context env))
        gs (gensym "caseval__")
        eenv (expr-env env)]
    (cond-> (str
             (when expr?
               (str "var " gs ";\n"))
             (str "switch (" (emit v eenv) ") {")
             (str/join (map (fn [test then]
                              (str/join
                               (map (fn [test]
                                      (str (str "case " (emit test eenv) ":\n")
                                           (if expr?
                                             (str gs " = " then)
                                             (emit then env))
                                           "\nbreak;\n"))
                                    test)))
                            tests thens))
             (when default
               (str "default:\n"
                    (if expr?
                      (str gs " = " (emit default eenv))
                      (emit default env))))
             (when expr?
               (str "return " gs ";"))
             "}")
      expr? (wrap-iife))))

(defmethod emit-special 'recur [_ env [_ & exprs]]
  (let [bindings *recur-targets*
        temps (repeatedly (count exprs) gensym)
        eenv (expr-env env)]
    (when-let [cb (:recur-callback env)]
      (cb bindings))
    (str
     (str/join ""
               (map (fn [temp expr]
                      (statement (format "let %s = %s"
                                         temp (emit expr eenv))))
                    temps exprs)
               )
     (str/join ""
               (map (fn [binding temp]
                      (statement (format "%s = %s"
                                         binding temp)))
                    bindings temps)
               )
     "continue;\n")))

(defn emit-var [more env]
  (apply str
         (interleave (map (fn [[name expr]]
                            (str "var " (emit name env) " = "
                                 (emit expr (assoc env :context :expr))))
                          (partition 2 more))
                     (repeat statement-separator))))

(defmethod emit-special 'def [_type env [_const & more]]
  (let [name (first more)]
    (swap! *public-vars* conj (munge* name))
    (emit-var more env)))

(declare emit-do)

(defn wrap-await [s]
  (format "(%s)" (str "await " s)))

(defmethod emit-special 'js/await [_ env [_await more]]
  (wrap-await (emit more env)))

(defn wrap-iife [s]
  (cond-> (format "(%sfunction () {\n %s\n})()" (if *async* "async " "") s)
    *async* (wrap-await)))

(defmethod emit-special 'let [type env [_let bindings & more]]
  (emit (core-let bindings more) env)
  #_(prn (core-let bindings more)))

(defn process-require-clause [[libname & {:keys [refer as]}]]
  (let [[libname suffix] (.split libname "$" 2)
        [p & _props] (when suffix
                       (.split suffix "."))]
    (str
     (when (and as (= "default" p))
       (statement (format "import %s from '%s'" as libname)))
     (when (and as (not p))
       (statement (format "import * as %s from '%s'" as libname)))
     (when refer
       (statement (format "import { %s } from '%s'"  (str/join ", " refer) libname))))))

(defmethod emit-special 'ns [_type _env [_ns _name & clauses]]
  (reduce (fn [acc [k & exprs]]
            (if (= :require k)
              (str acc (str/join "" (map process-require-clause exprs)))
              acc))
          ""
          clauses
          ))

(defmethod emit-special 'funcall [_type env [fname & args :as expr]]
  (emit-wrap env
             (str
              (emit fname (expr-env env))
              (comma-list (emit-args env args)))))

(defmethod emit-special 'str [_type env [str & args]]
  (apply clojure.core/str (interpose " + " (emit-args env args))))

(defn emit-method [env obj method args]
  (let [eenv (expr-env env)]
    (emit-wrap env (str (emit obj eenv) "."
                        (str method)
                        (comma-list (emit-args env args))))))

(defn emit-aget [env var idxs]
  (emit-wrap env (apply str
                        (emit var (expr-env env))
                        (interleave (repeat "[") (emit-args env idxs) (repeat "]")))))

(defmethod emit-special '. [_type env [_period obj method & args]]
  (let [[method args] (if (seq? method)
                        [(first method) (rest method)]
                        [method args])
        method-str (str method)]
    (if (str/starts-with? method-str "-")
      (emit-aget env obj [(subs method-str 1)])
      (emit-method env obj (symbol method-str) args))) #_(emit-method env obj method args))

(defmethod emit-special 'if [_type env [_if test then else]]
  (swap! *imported-core-vars* conj 'truth_)
  (if (= :expr (:context env))
    (->> (let [env (assoc env :context :expr)]
           (format "(%s) ? (%s) : (%s)"
                   (emit test env)
                   (emit then env)
                   (emit else env)))
         (emit-wrap env))
    (str (format "if (truth_(%s)) {\n"
                 (emit test (assoc env :context :expr)))
         (emit then env)
         "}"
         (when (some? else)
           (str " else {\n"
                (emit else env)
                "}")))))

(defmethod emit-special 'aget [type env [_aget var & idxs]]
  (emit-aget env var idxs))

;; TODO: this should not be reachable in user space
(defmethod emit-special 'return [_type env [_return expr]]
  (statement (str "return " (emit (assoc env :context :expr) env))))

#_(defmethod emit-special 'delete [type [return expr]]
    (str "delete " (emit expr)))

(defmethod emit-special 'set! [_type env [_set! var val & more]]
  (assert (or (nil? more) (even? (count more))))
  (let [eenv (expr-env env)]
    (emit-wrap env (str (emit var eenv) " = " (emit val eenv) statement-separator
                        #_(when more (str (emit (cons 'set! more) env)))))))

(defmethod emit-special 'new [_type env [_new class & args]]
  (emit-wrap env (str "new " (emit class (expr-env env)) (comma-list (emit-args env args)))))

#_(defmethod emit-special 'inc! [_type env [_inc var]]
    (str (emit var env) "++"))

#_(defmethod emit-special 'dec! [_type env [_dec var]]
    (str (emit var env) "--"))

(defmethod emit-special 'dec [_type env [_ var]]
  (emit-wrap env (str "(" (emit var (assoc env :context :expr)) " - " 1 ")")))

(defmethod emit-special 'inc [_type env [_ var]]
  (emit-wrap env (str "(" (emit var (assoc env :context :expr)) " + " 1 ")")))

#_(defmethod emit-special 'defined? [_type env [_ var]]
    (str "typeof " (emit var env) " !== \"undefined\" && " (emit var env) " !== null"))

#_(defmethod emit-special '? [_type env [_ test then else]]
    (str (emit test env) " ? " (emit then env) " : " (emit else env)))

(defmethod emit-special 'and [_type env [_ & more]]
  (apply str (interpose "&&" (emit-args env more))))

(defmethod emit-special 'or [_type env [_ & more]]
  (apply str (interpose "||" (emit-args env more))))

(defn emit-do [env exprs]
  (let [bl (butlast exprs)
        l (last exprs)
        ctx (:context env)
        statement-env (assoc env :context :statement)
        iife? (and (seq bl) (= :expr ctx))
        s (cond-> (str (str/join "" (map #(statement (emit % statement-env)) bl))
                       (emit l (assoc env :context
                                      (if iife? :return
                                          ctx))))
            iife?
            (wrap-iife))]
    s))

(defmethod emit-special 'do [_type env [_ & exprs]]
  (emit-do env exprs))

(defmethod emit-special 'while [_type env [_while test & body]]
  (str "while (" (emit test) ") { \n"
       (emit-do env body)
       "\n }"))

;; TODO: re-implement
#_(defmethod emit-special 'doseq [_type env [_doseq bindings & body]]
    (str "for (" (emit (first bindings) env) " in " (emit (second bindings) env) ") { \n"
         (if-let [more (nnext bindings)]
           (emit (list* 'doseq more body) env)
           (emit-do body env))
         "\n }"))

(defn emit-var-declarations []
  #_(when-not (empty? @var-declarations)
      (apply str "var "
             (str/join ", " (map emit @var-declarations))
             statement-separator)))

(declare emit-function*)

(defn emit-function [env name sig body & [elide-function?]]
  (assert (or (symbol? name) (nil? name)))
  (assert (vector? sig))
  (binding [*recur-targets* sig]
    (let [recur? (volatile! nil)
          env (assoc env :recur-callback
                     (fn [coll]
                       (when (identical? sig coll)
                         (vreset! recur? true))))
          body (emit-do (assoc env :context :return) body)
          body (if @recur?
                 (format "while(true){
%s
break;}" body)
                 body)]
      (str (when-not elide-function?
             (str (when *async*
                    "async ") "function "))
           (comma-list (map munge sig)) " {\n"
           (when (:type env)
             (str "var self__ = this;"))
           body "\n}"))))

(defn emit-function* [env expr]
  (let [name (when (symbol? (first expr)) (first expr))
        expr (if name (rest expr) expr)
        expr (if (seq? (first expr))
               ;; TODO: multi-arity:
               (first expr)
               expr)]
    (if name
      (let [signature (first expr)
            body (rest expr)]
        (str (when *async*
               "async ") "function " name " "
             (emit-function env name signature body true)))
      (let [signature (first expr)
            body (rest expr)]
        (str (emit-function env nil signature body))))))

(defmethod emit-special 'fn* [_type env [_fn & sigs :as expr]]
  (let [async? (:async (meta expr))]
    (binding [*async* async?]
      (emit-function* env sigs))))

(defmethod emit-special 'fn [_type env [fn & sigs :as expr]]
  (let [expanded (apply core-fn expr {} sigs)]
    (emit expanded env)))

(defmethod emit-special 'try [_type env [_try & body :as expression]]
  (let [try-body (remove #(contains? #{'catch 'finally} (and (seq? %)
                                                             (first %)))
                         body)
        catch-clause (filter #(= 'catch (and (seq? %)
                                             (first %)))
                             body)
        finally-clause (filter #(= 'finally (and (seq? %)
                                                 (first %)))
                               body)]
    (cond
      (and (empty? catch-clause)
           (empty? finally-clause))
      (throw (new Exception (str "Must supply a catch or finally clause (or both) in a try statement! " expression)))

      (> (count catch-clause) 1)
      (throw (new Exception (str "Multiple catch clauses in a try statement are not currently supported! " expression)))

      (> (count finally-clause) 1)
      (throw (new Exception (str "Cannot supply more than one finally clause in a try statement! " expression)))

      :else
      (->> (cond-> (str "try{\n"
                        (emit-do env try-body)
                        "}\n"
                        (when-let [[_ _exception binding & catch-body] (first catch-clause)]
                          ;; TODO: only bind when exception type matches
                          (str "catch(" (emit binding (expr-env env)) "){\n"
                               (emit-do env catch-body)
                               "}\n"))
                        (when-let [[_ & finally-body] (first finally-clause)]
                          (str "finally{\n"
                               (emit-do (assoc env :context :statement) finally-body)
                               "}\n")))
             (not= :statement (:context env))
             (wrap-iife))
           (emit-wrap env)))))

#_(defmethod emit-special 'break [_type _env [_break]]
    (statement "break"))

(derive #?(:clj clojure.lang.Cons :cljs Cons) ::list)
(derive #?(:clj clojure.lang.IPersistentList :cljs IList) ::list)
(derive #?(:clj clojure.lang.LazySeq :cljs LazySeq) ::list)
#?(:cljs (derive List ::list))

(defn strip-core-symbol [sym]
  (let [sym-ns (namespace sym)]
    (if (and sym-ns
             (or (= "clojure.core" sym-ns)
                 (= "cljs.core" sym-ns)))
      (symbol (name sym))
      sym)))

(defmethod emit ::list [expr env]
  (if (:quote env)
    (do
      (swap! *imported-core-vars* conj 'list)
      (format "list(%s)"
              (str/join ", " (emit-args env expr))))
    (if (symbol? (first expr))
      (let [head* (first expr)
            head (strip-core-symbol head*)
            expr (if (not= head head*)
                   (with-meta (cons head (rest expr))
                     (meta expr))
                   expr)
            head-str (str head)]
        (cond
          (and (= (.charAt head-str 0) \.)
               (> (count head-str) 1)
               (not (= ".." head-str)))
          (emit-special '. env
                        (list* '.
                               (second expr)
                               (symbol (subs head-str 1))
                               (nnext expr)))
          (contains? built-in-macros head)
          (let [macro (built-in-macros head)
                new-expr (apply macro expr {} (rest expr))]
            (emit new-expr env))
          (and (> (count head-str) 1)
               (str/ends-with? head-str "."))
          (emit (list* 'new (symbol (subs head-str 0 (dec (count head-str)))) (rest expr))
                env)
          (special-form? head) (emit-special head env expr)
          (infix-operator? head) (emit-infix head env expr)
          (prefix-unary? head) (emit-prefix-unary head expr)
          (suffix-unary? head) (emit-suffix-unary head expr)
          :else (emit-special 'funcall env expr)))
      (if (list? expr)
        (emit-special 'funcall env expr)
        (throw (new Exception (str "invalid form: " expr)))))))

#?(:cljs (derive PersistentVector ::vector))

#_(defn wrap-expr [env s]
    (case (:context env)
      :expr (wrap-iife s)
      :statement s
      :return s))

(defmethod emit #?(:clj clojure.lang.IPersistentVector
                   :cljs ::vector) [expr env]
  (swap! *imported-core-vars* conj 'vector)
  (emit-wrap env (format "vector(%s)"
                         (str/join ", " (emit-args env expr)))))

#?(:cljs (derive PersistentArrayMap ::map))
#?(:cljs (derive PersistentHashMap ::map))

(defmethod emit #?(:clj clojure.lang.IPersistentMap
                   :cljs ::map) [expr env]
  (let [expr-env (assoc env :context :expr)
        map-fn
        (when-not (::js (meta expr))
          (if (<= (count expr) 8)
            'arrayMap
            'hashMap))
        key-fn (if-not map-fn
                 name identity)
        mk-pair (fn [pair] (str (emit (key-fn (key pair)) expr-env) (if map-fn ", " ": ")
                                (emit (val pair) expr-env)))
        keys (str/join ", " (map mk-pair (seq expr)))]
    (when map-fn
      (swap! *imported-core-vars* conj map-fn))
    (->> (if map-fn
           (format "%s(%s)" map-fn keys)
           (format "({ %s })" keys))
         (emit-wrap env))))

(defmethod emit #?(:clj clojure.lang.PersistentHashSet
                   :cljs PersistentHashSet)
  [expr env]
  (swap! *imported-core-vars* conj 'hash_set)
  (emit-wrap env
             (format "%s%s" "hash_set"
                     (comma-list (emit-args env expr)))))

(defn transpile-form [f]
  (emit f {:context :statement}))

(def cherry-parse-opts
  (e/normalize-opts
   {:all true
    :end-location false
    :location? seq?
    :readers {'js #(vary-meta % assoc ::js true)}
    :read-cond :allow
    :features #{:cljc}}))

(defn transpile-string* [s]
  (let [rdr (e/reader s)
        opts cherry-parse-opts]
    (loop [transpiled ""]
      (let [next-form (e/parse-next rdr opts)]
        (if (= ::e/eof next-form)
          transpiled
          (let [next-t (transpile-form next-form)
                next-js (some-> next-t not-empty (statement))]
            (recur (str transpiled next-js))))))))

(defn transpile-string
  ([s] (transpile-string s nil))
  ([s {:keys [elide-exports
              elide-imports]}]
   (let [core-vars (atom #{})
         public-vars (atom #{})]
     (binding [*imported-core-vars* core-vars
               *public-vars* public-vars]
       (let [transpiled (transpile-string* s)
             transpiled (if-let [core-vars (and (not elide-imports)
                                                (seq @core-vars))]
                          (str (format "import { %s } from 'cherry-cljs/cljs.core.js'\n"
                                       (str/join ", " core-vars))
                               transpiled)
                          transpiled)
             transpiled (str transpiled
                             (when-not elide-exports
                               (str (format "\nexport { %s }\n"
                                            (str/join ", " (disj @public-vars "default$")))
                                    (when (contains? @public-vars "default$")
                                      "export default default$\n"))))]
         transpiled)))))

#?(:cljs
   (defn slurp [f]
     (fs/readFileSync f "utf-8")))

#?(:cljs
   (defn spit [f s]
     (fs/writeFileSync f s "utf-8")))

#?(:cljs
   (defn resolve-file [prefix suffix]
     (if (str/starts-with? suffix "./")
       (str/join "/" (concat [(js/process.cwd)]
                             (butlast (str/split prefix "/"))
                             [(subs suffix 2)]))
       suffix)))

#?(:cljs
   (def dyn-import (js/eval "(x) => { return import(x) }")))

#?(:cljs
   (defn scan-macros [file]
     (let [s (slurp file)
           maybe-ns (e/parse-next (e/reader s) cherry-parse-opts)]
       (when (and (seq? maybe-ns)
                  (= 'ns (first maybe-ns)))
         (let [[_ns _name & clauses] maybe-ns
               require-macros (some #(when (and (seq? %)
                                                (= :require-macros (first %)))
                                       (rest %))
                                    clauses)]
           (when require-macros
             (reduce (fn [prev require-macros]
                       (.then prev
                              (fn [_]
                                (let [[f & {:keys [refer]}] require-macros
                                      f (resolve-file file f)
                                      macros (-> (dyn-import f)
                                                 (.then (fn [macros]
                                                          (zipmap
                                                           refer
                                                           (map #(aget macros (munge %)) refer)))))]
                                  (.then macros
                                         (fn [macros]
                                           (set! built-in-macros
                                                 ;; hack
                                                 (merge built-in-macros macros))))))))
                     (js/Promise.resolve nil)
                     require-macros)))))))

(defn transpile-file [{:keys [in-file out-file]}]
  (let [out-file (or out-file
                     (str/replace in-file #".clj(s|c)$" ".mjs"))]
    (-> #?(:cljs (js/Promise.resolve (scan-macros in-file)))
        (.then #(transpile-string (slurp in-file)))
        (.then (fn [transpiled]
                 (spit out-file transpiled)
                 {:out-file out-file})))))

#_(defn compile! [s]
    (prn :s s)
    (let [expr (e/parse-string s {:row-key :line
                                  :col-key :column
                                  :end-location false})
          compiler-env (ana-api/empty-state)]
      (prn :expr expr (meta expr))
      (binding [cljs.env/*compiler* compiler-env
                ana/*cljs-ns* 'cljs.user]
        (let [analyzed (ana/analyze (ana/empty-env) expr)]
          (prn (keys analyzed))
          (prn (compiler/emit-str analyzed))))))
