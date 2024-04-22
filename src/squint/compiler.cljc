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
                                          *aliases* *cljs-ns* *excluded-core-vars* *imported-vars* *public-vars*
                                          comma-list emit emit-args emit-infix emit-return escape-jsx
                                          expr-env infix-operator? prefix-unary? suffix-unary?]]
   [squint.defclass :as defclass]
   [squint.internal.deftype :as deftype]
   [squint.internal.destructure :refer [core-let]]
   [squint.internal.fn :refer [core-defmacro core-defn core-defn- core-fn]]
   [squint.internal.loop :as loop]
   [squint.internal.macros :as macros]
   [squint.internal.protocols :as protocols])
  #?(:cljs (:require-macros [squint.resource :refer [edn-resource]])))


(defn emit-keyword [expr env]
  ;; emitting string already emits return
  (emit (str (subs (str expr) 1)) env))

(def special-forms (set ['var '. 'if 'funcall 'fn 'fn* 'quote 'set!
                         'return 'delete 'new 'do 'aget 'while
                         'inc! 'dec! 'dec 'inc 'defined?
                         '? 'try 'break 'throw 'not
                         'const 'let 'let* 'ns 'def 'loop*
                         'recur 'js* 'case* 'deftype* 'letfn*
                         ;; js
                         'js/await 'js-await 'js/typeof
                         ;; prefixed to avoid conflicts
                         'squint-compiler-jsx
                         'squint-compiler-html
                         'require 'squint.defclass/defclass* 'squint.defclass/super*
                         'clj->js
                         'squint.impl/for-of
                         'squint.impl/defonce]))

(def built-in-macros (merge {'-> macros/core->
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
                             'condp macros/core-condp
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
                             'defn- core-defn-
                             'instance? macros/core-instance?
                             'time macros/core-time
                             'declare macros/core-declare
                             'letfn macros/core-letfn
                             'defclass defclass/defclass
                             'js-template defclass/js-template
                             'or macros/core-or
                             'and macros/core-and
                             'assert macros/core-assert
                             'simple-benchmark macros/simple-benchmark}
                            cc/common-macros))

(def core-config {:vars (edn-resource "squint/core.edn")})

(def core-vars (conj (:vars core-config) 'goog_typeOf))

(defn special-form? [expr]
  (or
   (contains? cc/special-forms expr)
   (contains? special-forms expr)))

(defn emit-prefix-unary [_type [operator arg]]
  (str operator (emit arg)))

(defn emit-suffix-unary [_type [operator arg]]
  (str (emit arg) operator))

(defmulti emit-special (fn [disp _env & _args] disp))

(defmethod emit-special 'not [_ env [_ form]]
  (let [js (emit form (expr-env env))]
    (if (:bool js)
      (emit-return (cc/bool-expr (str "!" js)) env)
      (cc/bool-expr
       (emit (list 'js* (format "~{}(%s)" js) 'clojure.core/not)
             env)))))

(defmethod emit-special 'js/typeof [_ env [_ form]]
  (emit-return (str "typeof " (emit form (expr-env env))) env))

(defmethod emit-special 'clj->js [_ env [_ form]]
  (emit form env))

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

(defmethod emit-special 'let [_type env [_let bindings & more]]
  (emit (core-let env bindings more) env))

(defmethod emit-special 'squint.impl/for-of [_type enc-env [_for-of [k v] body :as _expr]]
  (let [env (assoc enc-env :context :statement)
        gensym (:gensym env)
        local (gensym)
        env (update env :var->ident assoc local local)]
    (str (emit (list 'js* (str/replace "for (let %s of ~{})" "%s" (str local))
                     (list 'clojure.core/iterable v))
               env)
         " {\n"
         (emit (list 'clojure.core/let [k local]
                     body)
               (assoc env :context :statement))
         "\n}"
         (emit-return nil enc-env))))

(defmethod emit-special 'squint.impl/defonce [_type env [_defonce name init]]
  (emit (list 'do #_(list 'js* (str "var " (munge name) ";\n"))
              (if (:repl env)
                `(when-not (exists? ~(symbol *cljs-ns* name))
                   ~(vary-meta `(def ~name ~init)
                               assoc :squint.compiler/skip-var true))
                (vary-meta `(def ~name ~init)
                           assoc :squint.compiler/skip-var true)))
        env))

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

(defn strip-core-symbol [sym]
  (let [sym-ns (namespace sym)]
    (if (and sym-ns
             (or (= "clojure.core" sym-ns)
                 (= "cljs.core" sym-ns)))
      (symbol (name sym))
      sym)))

(defn emit-list [expr env]
  (let [env* env
        env (assoc env :jsx (::jsx (meta expr)))]
    (escape-jsx
     (let [fexpr (first expr)]
       (if (:quote env)
         (do
           (swap! *imported-vars* update "squintscript/core.js" (fnil conj #{}) 'list)
           (format "%slist(%s)"
                   (if-let [ca (:core-alias env)]
                     (str ca ".")
                     "")
                   (str/join ", " (emit-args env expr))))
         (cond (symbol? fexpr)
               (let [head* (first expr)
                     head (strip-core-symbol head*)
                     expr* expr
                     expr (if (not= head head*)
                            (with-meta (cons head (rest expr))
                              (meta expr))
                            expr)
                     head-str (str head)
                     macro (when (symbol? head)
                             (or (built-in-macros head)
                                 (let [ns (namespace head)
                                       nm (name head)
                                       ns-state @(:ns-state env)
                                       current-ns (:current ns-state)
                                       nms (symbol nm)
                                       current-ns-state (get ns-state current-ns)]
                                   (if ns
                                     (let [nss (symbol ns)]
                                       (or
                                        ;; used by cherry embed:
                                        (some-> env :macros (get nss) (get nms))
                                        (let [resolved-ns (get-in current-ns-state [:aliases nss] nss)]
                                          (get-in ns-state [:macros resolved-ns nms]))))
                                     (let [refers (:refers current-ns-state)]
                                       (when-let [macro-ns (get refers nms)]
                                         (get-in ns-state [:macros macro-ns nms])))))))]
                 (if macro
                   (let [;; fix for calling macro with more than 20 args
                         #?@(:cljs [macro (or (.-afn ^js macro) macro)])
                         new-expr (apply macro expr {:repl cc/*repl*
                                                     :gensym (:gensym env)
                                                     :ns {:name cc/*cljs-ns*}} (rest expr))]
                     (emit new-expr env))
                   (cond
                     (and (= (.charAt head-str 0) \.)
                          (> (count head-str) 1)
                          (not (= ".." head-str)))
                     (cc/emit-special '. env
                                      (list* '.
                                             (second expr)
                                             (symbol (subs head-str 1))
                                             (nnext expr)))
                     (and (> (count head-str) 1)
                          (str/ends-with? head-str "."))
                     (emit (list* 'new (symbol (subs head-str 0 (dec (count head-str)))) (rest expr))
                           env)
                     (special-form? head) (cc/emit-special head env expr)
                     (infix-operator? env head) (emit-infix head env expr)
                     (prefix-unary? head) (emit-prefix-unary head expr)
                     (suffix-unary? head) (emit-suffix-unary head expr)
                     :else (cc/emit-special 'funcall env expr*))))
               (keyword? fexpr)
               (let [[k obj & args] expr]
                 (emit (list* 'clojure.core/get obj k args) env))
               (or (map? fexpr)
                   (set? fexpr))
               (let [[obj k & args] expr]
                 (emit (list* 'clojure.core/get obj k args) env))
               (list? expr)
               (cc/emit-special 'funcall env expr)
               :else
               (throw (new Exception (str "invalid form: " expr))))))
     env*)))

(defn emit-vector [expr env]
  (if (and (:jsx env)
           (let [f (first expr)]
             (or (keyword? f)
                 (symbol? f))))
    (let [top-dynamic-expr (:top-has-dynamic-expr env)
          has-dynamic-expr? (or (:has-dynamic-expr env) (atom false))
          env (assoc env :has-dynamic-expr has-dynamic-expr?)
          v expr
          tag (first v)
          keyw? (keyword? tag)
          attrs (second v)
          attrs (when (map? attrs) attrs)
          elts (if attrs (nnext v) (next v))
          tag-name (symbol tag)
          fragment? (= '<> tag-name)
          tag-name* (if fragment?
                      (symbol "")
                      tag-name)
          tag-name (if (and (not fragment?) keyw?)
                     (subs (str tag) 1)
                     (emit tag-name* (expr-env (dissoc env :jsx))))]
      (if (and (not (:html env)) (:jsx env) (:jsx-runtime env))
        (let [single-child? (= (count elts) 1)]
          (emit (list (if single-child?
                        '_jsx '_jsxs)
                      (cond fragment? "_Fragment"
                            (keyword? tag)
                            (name tag-name)
                            :else tag-name*)
                      (let [elts (map #(emit % (expr-env env)) elts)
                            elts (map #(list 'js* (str %)) elts)
                            children
                            (if single-child?
                              (first elts)
                              (vec elts))]
                        (cond-> (or attrs {})
                          (seq children)
                          (assoc :children children))))
                env))
        (emit-return
         (cond->> (format "<%s%s>%s</%s>"
                          tag-name
                          (cc/jsx-attrs attrs env)
                          (let [env (expr-env env)]
                            (str/join "" (map #(emit % env) elts)))
                          tag-name)
           (:outer-html (meta expr)) (format "%s`%s`"
                                             (if-let [t (:tag (meta expr))]
                                               (emit t (expr-env (dissoc env :jsx :html)))
                                               (if @has-dynamic-expr?
                                                 (do
                                                   (when top-dynamic-expr
                                                     (reset! top-dynamic-expr true))
                                                   "squint_html.tag")
                                                 ""))))
         env)))
    (emit-return (format "[%s]"
                         (str/join ", " (emit-args env expr))) env)))

(defn emit-map [expr env]
  (if (every? #(or (string? %)
                   (keyword? %)
                   (and (:quote env)
                        (symbol? %))) (keys expr))
    (let [env* env
          env (dissoc env :jsx)
          expr-env (assoc env :context :expr)
          key-fn (fn [k] (if-let [ns (and (keyword? k) (namespace k))]
                           (str ns "/" (name k))
                           (name k)))
          mk-pair (fn [pair]
                    (let [k (key pair)]
                      (str (if (= :& k)
                             (str "...")
                             (str (emit (key-fn k) expr-env) ": "))
                           (emit (val pair) expr-env))))
          keys (str/join ", " (map mk-pair (seq expr)))]
      (escape-jsx (-> (format "({ %s })" keys)
                      (emit-return env))
                  env*))
    (let [expr (list* 'doto {} (map (fn [[k v]]
                                      (list 'clojure.core/unchecked-set k v)) expr))]
      (emit expr env))))

(defn emit-set [expr env]
  (emit-return
   (format "new Set([%s])"
           (str/join ", " (emit-args (expr-env env) expr)))
   env))

(defn transpile-form
  ([f] (transpile-form f nil))
  ([f env]
   (binding [cc/*repl* (:repl env cc/*repl*)]
     (str
      (emit f (merge {:ns-state (atom {})
                      :context :statement
                      :top-level true
                      :core-vars core-vars
                      :gensym (let [ctr (volatile! 0)]
                                (fn gensym*
                                  ([] (gensym* nil))
                                  ([sym]
                                   (let [next-id (vswap! ctr inc)]
                                     (symbol (str (if sym (munge sym)
                                                      "G__") next-id))))))
                      :emit {::cc/list emit-list
                             ::cc/vector emit-vector
                             ::cc/map emit-map
                             ::cc/keyword emit-keyword
                             ::cc/set emit-set
                             ::cc/special emit-special}} env))))))

(def ^:dynamic *jsx* false)

(defn jsx [form]
  (list 'squint-compiler-jsx form))

(defn html [form]
  (list 'squint-compiler-html form))

(defmethod emit-special 'squint-compiler-jsx [_ env [_ form]]
  (set! *jsx* true)
  (let [env (assoc env :jsx true)]
    (emit form env)))

(defmethod emit-special 'squint-compiler-html [_ env [_ form]]
  (let [env (assoc env :html true :jsx true)
        form (vary-meta form assoc :outer-html true)]
    (emit form env)))

(def squint-parse-opts
  (e/normalize-opts
   {:all true
    :end-location false
    :location? seq?
    :readers {'js #(vary-meta % assoc ::js true)
              'jsx jsx
              'html html}
    :read-cond :allow
    :features #{:squint :cljs}}))

(defn transpile-string*
  ([s] (transpile-string* s {}))
  ([s env]
   (let [env (merge {:ns-state (atom {})} env)
         rdr (e/reader s)
         opts squint-parse-opts]
     (loop [transpiled (if cc/*repl*
                         (str "globalThis." *cljs-ns* " = globalThis." *cljs-ns* " || {};\n")
                         "")]
       (let [opts (assoc opts :auto-resolve @*aliases*)
             next-form (e/parse-next rdr opts)]
         (if (= ::e/eof next-form)
           transpiled
           (let [next-t (-> (transpile-form next-form env)
                            not-empty)
                 next-js
                 (cc/save-pragma env next-t)]
             (recur (str transpiled next-js)))))))))

(defn compile-string*
  ([s] (compile-string* s nil))
  ([s opts] (compile-string* s opts nil))
  ([s {:keys [elide-exports
              elide-imports
              core-alias]
       :or {core-alias "squint_core"}
       :as opts} state]
   (let [opts (merge state opts)]
     (binding [cc/*core-package* "squint-cljs/core.js"
               cc/*target* :squint
               *jsx* false
               cc/*repl* (:repl opts cc/*repl*)]
       (let [has-dynamic-expr (atom false)
             opts (merge {:ns-state (atom {})
                          :top-level true} opts)
             imported-vars (atom {})
             public-vars (atom #{})
             aliases (atom {core-alias cc/*core-package*})
             jsx-runtime (:jsx-runtime opts)
             jsx-dev (:development jsx-runtime)
             imports (atom (if cc/*repl*
                             (str (format "var %s = await import('%s');\n"
                                          core-alias cc/*core-package*))
                             (format "import * as %s from '%s';\n"
                                     core-alias cc/*core-package*)))
             pragmas (atom {:js ""})]
         (binding [*imported-vars* imported-vars
                   *public-vars* public-vars
                   *aliases* aliases
                   *jsx* false
                   *excluded-core-vars* (atom #{})
                   *cljs-ns* (:ns opts *cljs-ns*)
                   cc/*target* :squint
                   cc/*async* (:async opts)]
           (let [transpiled (transpile-string* s (assoc opts
                                                        :core-alias core-alias
                                                        :imports imports
                                                        :jsx false
                                                        :pragmas pragmas
                                                        :top-has-dynamic-expr has-dynamic-expr))
                 jsx *jsx*
                 _ (when (and jsx jsx-runtime)
                     (swap! imports str
                            (format
                             "var {jsx%s: _jsx, jsx%s%s: _jsxs, Fragment: _Fragment } = await import('%s');\n"
                             (if jsx-dev "DEV" "")
                             (if jsx-dev "" "s")
                             (if jsx-dev "DEV" "")
                             (str (:import-source jsx-runtime
                                                  "react")
                                  (if jsx-dev
                                    "/jsx-dev-runtime"
                                    "/jsx-runtime")))))
                 _ (when @has-dynamic-expr
                     (swap! imports str
                            (if cc/*repl*
                              "var squint_html = await import('squint-cljs/src/squint/html.js');\n"
                              "import * as squint_html from 'squint-cljs/src/squint/html.js';\n")))
                 pragmas (:js @pragmas)
                 imports (when-not elide-imports @imports)
                 exports (when-not elide-exports
                           (str
                            (when-let [vars (disj @public-vars "default$")]
                              (when (seq vars)
                                (if cc/*repl*
                                  (str/join "\n"
                                            (map (fn [var]
                                                   (str "export const " var " = " (munge cc/*cljs-ns*) "." var ";"))
                                                 vars))
                                  (str (format "\nexport { %s }\n"
                                               (str/join ", " vars))))))
                            (when (contains? @public-vars "default$")
                              "export default default$\n")))]
             (assoc opts
                    :pragmas pragmas
                    :imports imports
                    :exports exports
                    :body transpiled
                    :javascript (str pragmas imports transpiled exports)
                    :jsx jsx
                    :ns *cljs-ns*
                    :ns-state (:ns-state opts)))))))))

#?(:cljs
   (defn clj-ize-opts [opts]
     (let [opts (js->clj opts :keywordize-keys true)]
       (cond-> opts
         (:context opts) (update :context keyword)
         (:ns opts) (update :ns symbol)
         (:elide_imports opts) (assoc :elide-imports (:elide_imports opts))
         (:elide_exports opts) (assoc :elide-exports (:elide_exports opts))))))

#?(:cljs
   (defn compileStringEx [s opts state]
     (let [res (compile-string* s (clj-ize-opts opts) (clj-ize-opts state))]
       (clj->js res))))

(defn compile-string
  ([s] (compile-string s nil))
  ([s opts]
   (let [opts #?(:cljs (if (object? opts)
                         (clj-ize-opts opts)
                         opts)
                 :default opts)
         {:keys [javascript]}
         (compile-string* s opts)]
     javascript)))
