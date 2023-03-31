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
   #?(:clj [squint.resource :refer [edn-resource]])
   [clojure.string :as str]
   [edamame.core :as e]
   [squint.compiler-common :as cc :refer [#?(:cljs Exception)
                                          #?(:cljs format)
                                          *aliases* *cljs-ns* *excluded-core-vars* *imported-vars* *public-vars* *repl*
                                          comma-list emit emit-args emit-infix emit-repl emit-special emit-return escape-jsx
                                          expr-env infix-operator? prefix-unary? statement suffix-unary?]]
   [squint.internal.deftype :as deftype]
   [squint.internal.destructure :refer [core-let]]
   [squint.internal.fn :refer [core-defmacro core-defn core-fn]]
   [squint.internal.loop :as loop]
   [squint.internal.macros :as macros]
   [squint.internal.protocols :as protocols])
  #?(:cljs (:require-macros [squint.resource :refer [edn-resource]])))


(defmethod emit #?(:clj clojure.lang.Keyword :cljs Keyword) [expr env]
  (-> (emit-return (str (pr-str (subs (str expr) 1))) env)
      (emit-repl env)))

(def special-forms (set ['var '. 'if 'funcall 'fn 'fn* 'quote 'set!
                         'return 'delete 'new 'do 'aget 'while
                         'inc! 'dec! 'dec 'inc 'defined? 'and 'or
                         '? 'try 'break 'throw 'not
                         'const 'let 'let* 'ns 'def 'loop*
                         'recur 'js* 'case* 'deftype* 'letfn*
                         ;; js
                         'js/await 'js-await 'js/typeof
                         ;; prefixed to avoid conflicts
                         'squint-compiler-jsx
                         'require
                         ]))

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
(reset! cc/core-vars core-vars)

(defn special-form? [expr]
  (or
   (contains? cc/special-forms expr)
   (contains? special-forms expr)))

(defn emit-prefix-unary [_type [operator arg]]
  (str operator (emit arg)))

(defn emit-suffix-unary [_type [operator arg]]
  (str (emit arg) operator))

(defmethod emit-special 'quote [_ env [_ form]]
  (emit-return (emit form (expr-env (assoc env :quote true))) env))

(defmethod emit-special 'not [_ env [_ form]]
  (emit-return (str "!" (emit form (expr-env env))) env))

(defmethod emit-special 'js/typeof [_ env [_ form]]
  (emit-return (str "typeof " (emit form (expr-env env))) env))

(defmethod emit-special 'quote [_ env [_ form]]
  (emit-return (emit form (expr-env (assoc env :quote true))) env))

#_(defmethod emit-special 'let* [_type enc-env [_let bindings & body]]
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

#_(defn wrap-await [s]
  (format "(%s)" (str "await " s)))

(defmethod emit-special 'let [_type env [_let bindings & more]]
  (emit (core-let bindings more) env)
  #_(prn (core-let bindings more)))

(defmethod emit-special 'if [_type env [_if test then else]]
  (if (= :expression (:context env))
    (-> (let [env (assoc env :context :expression)]
          (format "(%s) ? (%s) : (%s)"
                  (emit test env)
                  (emit then env)
                  (emit else env)))
        (emit-return env))
    (str (format "if (%s) {\n"
                 (emit test (assoc env :context :expression)))
         (emit then env)
         "}"
         (when (some? else)
           (str " else {\n"
                (emit else env)
                "}")))))

(defn emit-var-declarations []
  #_(when-not (empty? @var-declarations)
      (apply str "var "
             (str/join ", " (map emit @var-declarations))
             statement-separator)))

(defmethod emit-special 'fn [_type env [_fn & sigs :as expr]]
  (let [expanded (apply core-fn expr {} sigs)]
    (emit expanded env)))

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
                       ;; fix for calling macro with more than 20 args
                       #?@(:cljs [macro (or (.-afn ^js macro) macro)])
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
             (throw (new Exception (str "invalid form: " expr))))))
   env))

(derive #?(:bb (class (list))
           :clj clojure.lang.PersistentList$EmptyList
           :cljs EmptyList) ::empty-list)

(defmethod emit ::empty-list [_expr env]
  ;; NOTE: we can later optimize this to a constant, but (.-EMPTY List) is prone
  ;; to advanced optimization
  (emit '(list) env))

#?(:cljs (derive PersistentVector ::vector))

#_(defn wrap-expr [env s]
    (case (:context env)
      :expression (wrap-iife s)
      :statement s
      :return s))

(defn jsx-attrs [v env]
  (let [env (expr-env env)]
    (if v
      (str " "
           (str/join " "
                     (map (fn [[k v]]
                            (if (= :& k)
                              (str "{..." (emit v (dissoc env :jsx)) "}")
                              (str (name k) "=" (emit v (assoc env :jsx-attr true)))))
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
                     tag-name)
          tag-name (emit tag-name (expr-env (dissoc env :jsx)))]
      (emit-return (format "<%s%s>%s</%s>"
                         tag-name
                         (jsx-attrs attrs env)
                         (let [env (expr-env env)]
                           (str/join " " (map #(emit % env) elts)))
                         tag-name)
                 env))
    (->  (emit-return (format "[%s]"
                            (str/join ", " (emit-args env expr))) env)
         (emit-repl env))))

#?(:cljs (derive PersistentArrayMap ::map))
#?(:cljs (derive PersistentHashMap ::map))

(defmethod emit #?(:clj clojure.lang.IPersistentMap
                   :cljs ::map) [expr env]
  (let [env* env
        env (dissoc env :jsx)
        expr-env (assoc env :context :expression)
        key-fn (fn [k] (if-let [ns (and (keyword? k) (namespace k))]
                         (str ns "/" (name k))
                         (name k)))
        mk-pair (fn [pair] (str (emit (key-fn (key pair)) expr-env) ": "
                                (emit (val pair) expr-env)))
        keys (str/join ", " (map mk-pair (seq expr)))]
    (escape-jsx (-> (format "({ %s })" keys)
                    (emit-return env))
                env*)))

(defmethod emit #?(:clj clojure.lang.PersistentHashSet
                   :cljs PersistentHashSet)
  [expr env]
  (emit-return
   (format "new Set([%s])"
           (str/join ", " (emit-args (expr-env env) expr)))
   env))

(defn transpile-form
  ([f] (transpile-form f nil))
  ([f env]
   (emit f (merge {:context :statement
                   :top-level true} env))))

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

(defn transpile-string*
  ([s] (transpile-string* s {}))
  ([s env]
   (let [rdr (e/reader s)
         opts squint-parse-opts]
     (loop [transpiled ""]
       (let [opts (assoc opts :auto-resolve @*aliases*)
             next-form (e/parse-next rdr opts)]
         (if (= ::e/eof next-form)
           transpiled
           (let [next-t (transpile-form next-form env)
                 next-js (some-> next-t not-empty (statement))]
             (recur (str transpiled next-js)))))))))

(defn compile-string*
  ([s] (compile-string* s nil))
  ([s {:keys [elide-exports
              core-alias
              elide-imports]
       :or {core-alias "squint_core"}
       :as opts}]
   (binding [cc/*core-package* "squint-cljs/core.js"
             cc/*target* :squint
             *jsx* false]
     (let [imported-vars (atom {})
           public-vars (atom #{})
           aliases (atom {core-alias cc/*core-package*})
           imports (atom (format "import * as %s from '%s';\n"
                                 core-alias cc/*core-package*))]
       (binding [*imported-vars* imported-vars
                 *public-vars* public-vars
                 *aliases* aliases
                 *jsx* false
                 *excluded-core-vars* (atom #{})
                 *cljs-ns* *cljs-ns*
                 cc/*target* :squint]
         (let [transpiled (transpile-string* s (assoc opts
                                                      :core-alias core-alias
                                                      :imports imports))
               imports (when-not elide-imports @imports)
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
            :jsx *jsx*
            :ns *cljs-ns*}))))))

(defn compile-string
  ([s] (compile-string s nil))
  ([s opts]
   (let [{:keys [javascript]}
         (compile-string* s opts)]
     javascript)))

#_(defn compile! [s]
    (prn :s s)
    (let [expr (e/parse-string s {:row-key :line
                                  :col-key :column
                                  :end-location false})
          compiler-env (ana-api/empty-state)]
      (prn :expression expr (meta expr))
      (binding [cljs.env/*compiler* compiler-env
                ana/*cljs-ns* 'cljs.user]
        (let [analyzed (ana/analyze (ana/empty-env) expr)]
          (prn (keys analyzed))
          (prn (compiler/emit-str analyzed))))))
