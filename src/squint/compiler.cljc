;; Adapted from Scriptjure. Original copyright notice:

;; Copyright (c) Allen Rohner, 2009. All rights reserved.  The use
;; and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this
;; distribution.  By using this software in any fashion, you are
;; agreeing to be bound by the terms of this license.  You must not
;; remove this notice, or any other, from this software.

(ns squint.compiler
  (:require
   [clojure.string :as str]
   [com.reasonr.string :as rstr]
   [edamame.core :as e]
   #?(:cljs [goog.string.format])
   #?(:cljs [goog.string :as gstring])
   #?(:clj [squint.resource :refer [edn-resource]])
   [squint.internal.deftype :as deftype]
   [squint.internal.destructure :refer [core-let]]
   [squint.internal.fn :refer [core-defmacro core-defn core-fn]]
   [squint.internal.loop :as loop]
   [squint.internal.macros :as macros]
   [squint.internal.protocols :as protocols])
  #?(:cljs (:require-macros [squint.resource :refer [edn-resource]])))

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
(def ^:dynamic *aliases* (atom {}))
(def ^:dynamic *async* false)
(def ^:dynamic *imported-vars* (atom {}))
(def ^:dynamic *excluded-core-vars* (atom #{}))
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
  (if (and (:jsx env)
           (not (:jsx-attr env)))
    expr
    (emit-wrap env (pr-str expr))))

(defmethod emit #?(:clj clojure.lang.Keyword :cljs Keyword) [expr env]
  (emit-wrap env (str (pr-str (subs (str expr) 1)))))

(defn munge* [expr]
  (let [munged (str (munge expr))
        keep #{"import" "await"}]
    (cond-> munged
      (and (str/ends-with? munged "$")
           (contains? keep (str expr)))
      (str/replace #"\$$" ""))))

(declare core-vars)

(defn maybe-core-var [sym]
  (let [m (munge sym)]
    (when (and (contains? core-vars m)
             (not (contains? @*excluded-core-vars* m)))
      (swap! *imported-vars* update "squint-cljs/core.js" (fnil conj #{}) m)
      m)))

(defn escape-jsx [env expr]
  (if (:jsx env)
    (format "{%s}" expr)
    expr))

(defmethod emit #?(:clj clojure.lang.Symbol :cljs Symbol) [expr env]
  (if (:quote env)
    (emit-wrap env
               (escape-jsx env
                           (emit (list 'cljs.core/symbol
                                       (str expr))
                                 (dissoc env :quote))))
    (if (and (simple-symbol? expr)
             (str/includes? (str expr) "."))
      (let [[fname path] (str/split (str expr) #"\." 2)
            fname (symbol fname)]
        (escape-jsx env (str (emit fname (dissoc (expr-env env) :jsx))
                             "." path)))
      (let [munged-name (fn [expr] (munge* (name expr)))
            expr (if-let [sym-ns (namespace expr)]
                   (let [sn (symbol (name expr))]
                     (or (when (or (= "cljs.core" sym-ns)
                                   (= "clojure.core" sym-ns))
                           (some-> (maybe-core-var sn) munge))
                         (when (= "js" sym-ns)
                           (munge* (name expr)))
                         (when-let [resolved-ns (get @*aliases* (symbol sym-ns))]
                           (swap! *imported-vars* update resolved-ns (fnil conj #{}) (munged-name sn))
                           (str sym-ns "_" (munged-name sn)))
                         expr))
                   (if-let [renamed (get (:var->ident env) expr)]
                     (munge* (str renamed))
                     (or
                      (some-> (maybe-core-var expr) munge)
                      (let [m (munged-name expr)]
                        (prn :m m)
                        (str #_#_(munge *ns*) "." m)))))]
        (emit-wrap env
                   (escape-jsx env
                               (str expr)))))))

#?(:clj (defmethod emit #?(:clj java.util.regex.Pattern) [expr _env]
          (str \/ expr \/)))

(defmethod emit :default [expr env]
  ;; RegExp case moved here:
  ;; References to the global RegExp object prevents optimization of regular expressions.
  (emit-wrap env (str expr)))

(def special-forms (set ['var '. 'if 'funcall 'fn 'fn* 'quote 'set!
                         'return 'delete 'new 'do 'aget 'while
                         'inc! 'dec! 'dec 'inc 'defined? 'and 'or
                         '? 'try 'break 'throw 'not
                         'const 'let 'let* 'ns 'def 'loop*
                         'recur 'js* 'case* 'deftype* 'letfn*
                         ;; js
                         'js/await 'js/typeof
                         ;; prefixed to avoid conflicts
                         'squint-compiler-jsx]))

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
                      'some->> macros/core-some->>
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
                      'defn- core-defn
                      'instance? macros/core-instance?
                      'time macros/core-time
                      'declare macros/core-declare
                      'letfn macros/core-letfn})

(def core-config {:vars (edn-resource "squint/core.edn")})

(def core-vars (conj (:vars core-config) 'goog_typeOf))

(def prefix-unary-operators '#{!})

(def suffix-unary-operators '#{++ --})

(def infix-operators #{"+" "+=" "-" "-=" "/" "*" "%" "=" "==" "===" "<" ">" "<=" ">=" "!="
                       "<<" ">>" "<<<" ">>>" "!==" "&" "|" "&&" "||" "not=" "instanceof"})

(def chainable-infix-operators #{"+" "-" "*" "/" "&" "|" "&&" "||"})

(defn special-form? [expr]
  (contains? special-forms expr))

(defn infix-operator? [expr]
  (contains? infix-operators (name expr)))

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
  (let [env (assoc enc-env :context :expr)
        acount (count args)]
    (if (and (not (chainable-infix-operators (name operator))) (> acount 2))
      (emit (list 'cljs.core/and
                  (list operator (first args) (second args))
                  (list* operator (rest args))))
      (if (and (= '- operator)
               (= 1 acount))
        (str "-" (emit (first args) env))
        (->> (let [substitutions {'= "===" == "===" '!= "!=="
                                  'not= "!=="
                                  '+ "+"}]
               (str "(" (str/join (str " " (or (substitutions operator) operator) " ")
                                  (emit-args env args)) ")"))
             (emit-wrap enc-env))))))

(def ^:dynamic *recur-targets* [])

(declare emit-do wrap-iife)

(defmethod emit-special 'quote [_ env [_ form]]
  (emit-wrap env (emit form (expr-env (assoc env :quote true)))))

(defmethod emit-special 'not [_ env [_ form]]
  (emit-wrap env (str "!" (emit form (expr-env env)))))

(defmethod emit-special 'js/typeof [_ env [_ form]]
  (emit-wrap env (str "typeof " (emit form (expr-env env)))))

(defmethod emit-special 'letfn* [_ env [_ form & body]]
  (let [bindings (take-nth 2 form)
        fns (take-nth 2 (rest form))
        sets (map (fn [binding fn]
                    `(set! ~binding ~fn))
                  bindings fns)
        let `(let ~(vec (interleave bindings (repeat nil))) ~@sets ~@body)]
    (emit let env)))

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
                        rename? (not (:squint.compiler/no-rename vm))
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
};
%s"
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
                            (str "globalThis."
                                 (when *ns*
                                   (str (munge *ns*) ".") #_"var ") (emit name env) " = "
                                 (emit expr (assoc env :context :expr))
                                 "\n" "var " (emit name env) " = " "globalThis."
                                 (when *ns*
                                   (str (munge *ns*) ".") #_"var ") (emit name env))
                            )
                          (partition 2 more))
                     (repeat statement-separator))))

(defmethod emit-special 'def [_type env [_const & more]]
  ;;(prn *ns*)
  (let [name (first more)]
    (swap! *public-vars* conj (munge* name))
    (emit-var more env)))

(declare emit-do)

(defn wrap-await [s]
  (format "(%s)" (str "await " s)))

(defmethod emit-special 'js/await [_ env [_await more]]
  (emit-wrap env (wrap-await (emit more (expr-env env)))))

(defn wrap-iife [s]
  (cond-> (format "(%sfunction () {\n %s\n})()" (if *async* "async " "") s)
    *async* (wrap-await)))

(defmethod emit-special 'let [type env [_let bindings & more]]
  (emit (core-let bindings more) env)
  #_(prn (core-let bindings more)))

(defn resolve-ns [alias]
  (case alias
    (squint.string clojure.string) "squint-cljs/string.js"
    alias))

(defn process-require-clause [[libname & {:keys [refer as]}]]
  (let [libname (resolve-ns libname)
        [libname suffix] (.split libname "$" 2)
        [p & _props] (when suffix
                       (.split suffix "."))]
    (str
     (when (and as (= "default" p))
       (statement (format "import %s from '%s'" as libname)))
     (when (and (not as) (not p) (not refer))
       ;; import presumably for side effects
       (statement (format "import '%s'" libname)))
     (when as
       (swap! *imported-vars* update libname (fnil identity #{}))
       nil)
     (when refer
       (statement (format "import { %s } from '%s'"  (str/join ", " refer) libname))))))

(defmethod emit-special 'ns [_type _env [_ns name & clauses]]
  (set! *ns* name)
  (reset! *aliases*
          (->> clauses
               (some
                (fn [[k & exprs]]
                  (when (= :require k) exprs)))
               (reduce
                (fn [aliases [full as alias]]
                  (let [full (resolve-ns full)]
                    (case as
                      (:as :as-alias)
                      (assoc aliases alias full)
                      aliases)))
                {:current name})))
  (str
   (str "globalThis." (munge name) " = {} " )
   (reduce (fn [acc [k & exprs]]
             (cond
               (= :require k)
               (str acc (str/join "" (map process-require-clause exprs)))
               (= :refer-clojure k)
               (let [{:keys [exclude]} exprs]
                 (swap! *excluded-core-vars* into exclude)
                 acc)
               :else acc))
           ""
           clauses)))

(defmethod emit-special 'funcall [_type env [fname & args :as _expr]]
  (emit-wrap env
             (str
              (emit fname (expr-env env))
              ;; this is needed when calling keywords, symbols, etc. We could
              ;; optimize this later by inferring that we're not directly
              ;; calling a `function`.
              #_(when-not interop? ".call")
              (comma-list (emit-args env
                                     args #_(if interop? args
                                                (cons nil args)))))))

(defmethod emit-special 'str [_type env [_str & args]]
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
  (if (= :expr (:context env))
    (->> (let [env (assoc env :context :expr)]
           (format "(%s) ? (%s) : (%s)"
                   (emit test env)
                   (emit then env)
                   (emit else env)))
         (emit-wrap env))
    (str (format "if (%s) {\n"
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
  (emit-wrap env (apply str (interpose " && " (emit-args env more)))))

(defmethod emit-special 'or [_type env [_ & more]]
  (emit-wrap env (apply str (interpose " || " (emit-args env more)))))

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

(defn ->sig [env sig]
  (reduce (fn [[env sig seen] param]
            (if (contains? seen param)
              (let [new-param (gensym param)
                    env (update env :var->ident assoc param new-param)
                    sig (conj sig new-param)
                    seen (conj seen param)]
                [env sig seen])
              [env (conj sig param) (conj seen param)]))
          [env [] #{}]
          sig))

(defn emit-function [env name sig body & [elide-function?]]
  (assert (or (symbol? name) (nil? name)))
  (assert (vector? sig))
  (let [[env sig] (->sig env sig)]
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
             (comma-list (map (fn [sym]
                                (let [munged (munge sym)]
                                  (if (:... (meta sym))
                                    (str "..." munged)
                                    munged))) sig)) " {\n"
             (when (:type env)
               (str "var self__ = this;"))
             body "\n}")))))

(defn emit-function* [env expr]
  (let [name (when (symbol? (first expr)) (first expr))
        expr (if name (rest expr) expr)
        expr (if (seq? (first expr))
               ;; TODO: multi-arity:
               (first expr)
               expr)]
    (->> (if name
           (let [signature (first expr)
                 body (rest expr)]
             (str (when *async*
                    "async ") "function " name " "
                  (emit-function env name signature body true)))
           (let [signature (first expr)
                 body (rest expr)]
             (str (emit-function env nil signature body))))
         (emit-wrap env))))

(defmethod emit-special 'fn* [_type env [_fn & sigs :as expr]]
  (let [async? (:async (meta expr))]
    (binding [*async* async?]
      (emit-function* env sigs))))

(defmethod emit-special 'fn [_type env [_fn & sigs :as expr]]
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
  (escape-jsx
   env
   (let [env (dissoc env :jsx)]
     (if (:quote env)
       (do
         (swap! *imported-vars* update "squintscript/core.js" (fnil conj #{}) 'list)
         (format "list(%s)"
                 (str/join ", " (emit-args env expr))))
       (cond (symbol? (first expr))
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
             (keyword? (first expr))
             (let [[k obj & args] expr]
               (emit (list* 'get obj k args) env))
             (list? expr)
             (emit-special 'funcall env expr)
             :else
             (throw (new Exception (str "invalid form: " expr))))))))

#?(:cljs (derive PersistentVector ::vector))

#_(defn wrap-expr [env s]
    (case (:context env)
      :expr (wrap-iife s)
      :statement s
      :return s))

(defn jsx-attrs [v env]
  (let [env (expr-env env)]
    (if v
      (str " "
           (str/join " "
                     (map (fn [[k v]]
                            (str (name k) "=" (emit v (assoc env :jsx-attr true))))
                          v)))
      "")))

(defmethod emit #?(:clj clojure.lang.IPersistentVector
                   :cljs ::vector) [expr env]
  (if (and (:jsx env)
           (let [f (first expr)]
             (or (keyword? f)
                 (symbol? f))))
    (let [v expr
          tag (first v)
          attrs (second v)
          attrs (when (map? attrs) attrs)
          elts (if attrs (nnext v) (next v))
          tag-name (symbol tag)
          tag-name (if (= '<> tag-name)
                     (symbol "")
                     tag-name)]
      (emit-wrap env (format "<%s%s>%s</%s>"
                             tag-name
                             (jsx-attrs attrs env)
                             (let [env (expr-env env)]
                               (str/join " " (map #(emit % env) elts)))
                             tag-name)))
    (emit-wrap env (format "[%s]"
                           (str/join ", " (emit-args env expr))))))

#?(:cljs (derive PersistentArrayMap ::map))
#?(:cljs (derive PersistentHashMap ::map))

(defmethod emit #?(:clj clojure.lang.IPersistentMap
                   :cljs ::map) [expr env]
  (let [env* env
        env (dissoc env :jsx)
        expr-env (assoc env :context :expr)
        key-fn (fn [k] (if-let [ns (and (keyword? k) (namespace k))]
                         (str ns "/" (name k))
                         (name k)))
        mk-pair (fn [pair] (str (emit (key-fn (key pair)) expr-env) ": "
                                (emit (val pair) expr-env)))
        keys (str/join ", " (map mk-pair (seq expr)))]
    (escape-jsx env*
                (->> (format "({ %s })" keys)
                     (emit-wrap env)))))

(defmethod emit #?(:clj clojure.lang.PersistentHashSet
                   :cljs PersistentHashSet)
  [expr env]
  (emit-wrap env
             (format "new Set([%s])"
                     (str/join ", " (emit-args (expr-env env) expr)))))

(defn transpile-form [f]
  (emit f {:context :statement}))

(def ^:dynamic *jsx* false)

(defn jsx [form]
  (list 'squint-compiler-jsx form))

(defmethod emit-special 'squint-compiler-jsx [_ env [_ form]]
  (set! *jsx* true)
  (let [env (assoc env :jsx true)]
    (emit form env)))

(def squint-parse-opts
  (e/normalize-opts
   {:all true
    :end-location false
    :location? seq?
    :readers {'js #(vary-meta % assoc ::js true)
              'jsx jsx}
    :read-cond :allow
    :features #{:cljc}}))

(defn transpile-string* [s]
  (let [rdr (e/reader s)
        opts squint-parse-opts]
    (loop [transpiled ""]
      (let [opts (assoc opts :auto-resolve @*aliases*)
            next-form (e/parse-next rdr opts)]
        (if (= ::e/eof next-form)
          transpiled
          (let [next-t (transpile-form next-form)
                next-js (some-> next-t not-empty (statement))]
            (recur (str transpiled next-js))))))))

(defn compile-string*
  ([s] (compile-string* s nil))
  ([s {:keys [elide-exports
              elide-imports]}]
   (let [imported-vars (atom {})
         public-vars (atom #{})
         aliases (atom {})]
     (binding [*imported-vars* imported-vars
               *public-vars* public-vars
               *aliases* aliases
               *jsx* false
               *excluded-core-vars* (atom #{})]
       (let [transpiled (transpile-string* s)
             imports (when-not elide-imports
                       (let [ns->alias (zipmap (vals @aliases)
                                               (keys @aliases))]
                         (reduce (fn [acc [k v]]
                                   (let [alias (get ns->alias k)
                                         symbols (if alias
                                                   (map #(str % " as " (str alias "_" %)) v)
                                                   v)]
                                     (str acc
                                          (format "import { %s } from '%s'\n"
                                                  (str/join ", " symbols)
                                                  k))))
                                 ""
                                 @imported-vars)))
             exports (when-not elide-exports
                       (str
                        (when-let [vars (disj @public-vars "default$")]
                          (when (seq vars)
                            (str (format "\nexport { %s }\n"
                                         (str/join ", " vars)))
                            ))
                        (when (contains? @public-vars "default$")
                          "export default default$\n")))]
         {:imports imports
          :exports exports
          :body transpiled
          :javascript (str imports transpiled exports)
          :jsx *jsx*})))))

(defn compile-string
  ([s] (compile-string s nil))
  ([s opts]
   (let [{:keys [imports exports body]}
         (compile-string* s opts)]
     (str imports body exports))))

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
