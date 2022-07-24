;;; scriptjure -- a library for generating javascript from Clojure s-exprs

;; by Allen Rohner, http://arohner.blogspot.com
;;                  http://www.reasonr.com
;; October 7, 2009

;; Copyright (c) Allen Rohner, 2009. All rights reserved.  The use
;; and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this
;; distribution.  By using this software in any fashion, you are
;; agreeing to be bound by the terms of this license.  You must not
;; remove this notice, or any other, from this software.

;; This library generates javascript from Clojure s-exprs. To use it,
;; (js (fn foo [x] (var x (+ 3 5)) (return x)))
;;  returns a string, "function foo (x) { var x = (3 + 5); return x; }"
;;
;; See the README and the tests for more information on what is supported.
;;
;; The library is intended to generate javascript glue code in Clojure
;; webapps. One day it might become useful enough to write entirely
;; JS libraries in clojure, but it's not there yet.
;;
;;

(ns #^{:author "Allen Rohner"
       :doc "A library for generating javascript from Clojure."}
    cherry.transpiler
  (:require
   #?(:cljs ["fs" :as fs])
   #?(:cljs [goog.string.format])
   #?(:cljs [goog.string :as gstring])
   #?(:clj [cherry.resource :as resource])
   [cherry.internal.destructure :refer [core-let]]
   [cherry.internal.fn :refer [core-defn core-fn]]
   [cherry.internal.loop :as loop]
   [cherry.internal.macros :as macros]
   [clojure.string :as str]
   [com.reasonr.string :as rstr]
   #_[cherry.vendor.cljs.analyzer :as ana]
   #_[cherry.vendor.cljs.compiler :as compiler]
   #_[cljs.analyzer.api :as ana-api]
   #_[cljs.env]
   [edamame.core :as e])
  #?(:cljs (:require-macros [cherry.resource :as resource])))

#?(:cljs (def Exception js/Error))

#?(:cljs (def format gstring/format))

(defmulti emit (fn [expr _env] (type expr)))

(defmulti emit-special (fn [disp _env & _args] disp))

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

(defn emit-wrap [env s]
  ;; (prn :wrap s (:contet env))
  (if (= :return (:context env))
    (format "return %s;" s)
    s))

(defmethod emit nil [_ env]
  (emit-wrap env "null"))

(defmethod emit #?(:clj java.lang.Integer :cljs js/Number) [expr env]
  (->> (str expr)
       (emit-wrap env)))

(defmethod emit #?(:clj java.lang.String :cljs js/String) [^String expr env]
  (emit-wrap env (pr-str expr)))

(defmethod emit #?(:clj clojure.lang.Keyword :cljs Keyword) [expr env]
  (swap! *imported-core-vars* conj 'keyword)
  (emit-wrap env (str (format "keyword(%s)" (pr-str (subs (str expr) 1))))))

(defn munge* [expr]
  (let [munged (str (munge expr))]
    (cond-> munged
      (and (str/ends-with? munged "$")
           (not= (str expr) "default"))
      (str/replace #"\$$" ""))))

(defmethod emit #?(:clj clojure.lang.Symbol :cljs Symbol) [expr env]
  (let [expr-ns (namespace expr)
        js? (= "js" expr-ns)
        expr-ns (when-not js? expr-ns)
        expr (if-let [renamed (get (:var->ident env) expr)]
               (str renamed)
               (str expr-ns (when expr-ns
                              ".")
                    (munge* (name expr))))]
    (emit-wrap env (str expr))))

;; #?(:clj (defmethod emit #?(:clj java.util.regex.Pattern) [expr _env]
;;           (str \/ expr \/)))

#?(:cljs
   (defmethod emit :default [expr env]
     ;; RegExp case moved here:
     ;; References to the global RegExp object prevents optimization of regular expressions.
     (emit-wrap env (if (instance? js/RegExp expr)
                      (str \/ expr \/)
                      (str expr)))))

(def special-forms (set ['var '. '.. 'if 'funcall 'fn 'fn* 'quote 'set!
                         'return 'delete 'new 'do 'aget 'while 'doseq
                         'inc! 'dec! 'dec 'inc 'defined? 'and 'or
                         '? 'try 'break
                         'await 'const 'defn 'let 'let* 'ns 'def 'loop*
                         'recur]))

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
                      'loop loop/core-loop})

(def core-config (resource/edn-resource "cherry/cljs.core.edn"))

(def core-vars (:vars core-config))

(def prefix-unary-operators (set ['!]))

(def suffix-unary-operators (set ['++ '--]))

(def infix-operators (set ['+ '+= '- '-= '/ '* '% '== '=== '< '> '<= '>= '!=
                           '<< '>> '<<< '>>> '!== '& '| '&& '|| '= 'not= 'instanceof]))

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

(defn emit-infix [_type enc-env [operator & args]]
  (let [env (assoc enc-env :context :expr)]
    (when (and (not (chainable-infix-operators operator)) (> (count args) 2))
      (throw (Exception. (str "operator " operator " supports only 2 arguments"))))
    (->> (let [substitutions {'= '=== '!= '!== 'not= '!==}]
           (str "(" (str/join (str " " (or (substitutions operator) operator) " ")
                              (map #(emit % env) args)) ")"))
         (emit-wrap enc-env))))

(def ^{:dynamic true} var-declarations nil)

#_(defmethod emit-special 'var [type env [var & more]]
    (apply swap! var-declarations conj (filter identity (map (fn [name i] (when (odd? i) name)) more (iterate inc 1))))
    (apply str (interleave (map (fn [[name expr]]
                                  (str (when-not var-declarations "var ") (emit env name) " = " (emit env expr)))
                                (partition 2 more))
                           (repeat statement-separator))))

(defn emit-const [more env]
  (apply str
         (interleave (map (fn [[name expr]]
                            (str "const " (emit name env) " = "
                                 (emit expr (assoc env :context :expr))))
                          (partition 2 more))
                     (repeat statement-separator))))


(def ^:dynamic *recur-targets* [])

(declare emit-do wrap-iife)

(defn emit-let [enc-env bindings body is-loop]
  (let [context (:context enc-env)
        env (assoc enc-env :context :expr)
        partitioned (partition 2 bindings)
        iife? (= :expr context)
        upper-var->ident (:var->ident enc-env)
        [bindings var->ident]
        (reduce (fn [[acc var->ident] [var-name rhs]]
                  (let [renamed (gensym var-name)
                        lhs (str renamed)
                        rhs (emit rhs (assoc env :var->ident var->ident))
                        expr (format "let %s = %s;\n" lhs rhs)
                        var->ident (assoc var->ident var-name renamed)]
                    [(str acc expr) var->ident]))
                ["" upper-var->ident]
                partitioned)
        enc-env (assoc enc-env :var->ident var->ident)]
    (cond->> (str
              #_(let [names renamed]
                  (statement (str "let " (str/join ", " names))))
              bindings #_(apply str (interleave
                                     (map (fn [[name expr]]
                                            (str (emit name env) " = "
                                                 (emit expr env)))
                                          partitioned)
                                     (repeat statement-separator)))
              (when is-loop
                (str "while(true){\n"))
              (binding [*recur-targets* (map first partitioned)]
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

(defmethod emit-special 'loop* [_ env [_ bindings & body]]
  (emit-let env bindings body true))

#_(binding [*recur-targets* bindings]
    (format "while (true) {
%s

break; }"
            (emit (list* 'let bindings body) env)))

(defmethod emit-special 'recur [_ env [_ & exprs]]
  (let [bindings *recur-targets*
        temps (repeatedly (count exprs) gensym)]
    (str
     (str/join "\n"
               (map (fn [temp expr]
                      (statement (format "%s = %s"
                                         temp (emit expr env))))
                    temps exprs)
               )
     (str/join "\n"
               (map (fn [binding temp]
                      (statement (format "%s = %s"
                                         (emit binding env) temp)))
                    bindings temps)
               )
     "continue;\n")))

(defmethod emit-special 'def [_type env [_const & more]]
  (let [name (first more)]
    (swap! *public-vars* conj (munge* name))
    (emit-const more env)))

(declare emit-do)

(defn wrap-await [s]
  (format "(%s)" (str "await " s)))

(defmethod emit-special 'await [_ env [_await more]]
  (wrap-await (emit more env)))

(defn wrap-iife [s]
  (cond-> (format "(%sfunction () {\n %s\n})()" (if *async* "async " "") s)
    *async* (wrap-await)))

(defmethod emit-special 'let [type env [_let bindings & more]]
  (emit (core-let bindings more) env)
  #_(prn (core-let bindings more)))

(defn process-require-clause [[libname & {:keys [refer as]}]]
  (str (when as
         (statement (format "import * as %s from '%s'" as libname)))
       (when refer
         (statement (format "import { %s } from '%s'"  (str/join ", " refer) libname)))))

(defmethod emit-special 'ns [_type _env [_ns _name & clauses]]
  (reduce (fn [acc [k & exprs]]
            (if (= :require k)
              (str acc (str/join "" (map process-require-clause exprs)))
              acc))
          ""
          clauses
          ))

(defn emit-args [env args]
  (let [env (assoc env :context :expr)]
    (map #(emit % env) args)))

(defmethod emit-special 'funcall [_type env [name & args :as expr]]
  (let [s (if (and (symbol? name)
                   (= "cljs.core" (namespace name)))
            (emit (with-meta (list* (symbol (clojure.core/name name)) args)
                    (meta expr)) env)
            (emit-wrap env
                       (str (if (and (list? name) (= 'fn (first name))) ; function literal call
                              (str "(" (emit name env) ")")
                              (let [name
                                    (if (contains? core-vars name)
                                      (let [name (symbol (munge* name))]
                                        (swap! *imported-core-vars* conj name)
                                        name)
                                      name)]
                                (emit name (assoc env :context :expr))))
                            (comma-list (emit-args env args)))))]
    ;; (prn :-> s)
    s #_(emit-wrap env s)))

(defmethod emit-special 'str [type env [str & args]]
  (apply clojure.core/str (interpose " + " (emit-args env args))))

(defn emit-method [env obj method args]
  (str (emit obj) "." (emit method) (comma-list (emit-args env args))))

(defmethod emit-special '. [type env [period obj method & args]]
  (emit-method env obj method args))

(defmethod emit-special '.. [type env [dotdot & args]]
  (apply str (interpose "." (emit-args env args))))

(defmethod emit-special 'if [type env [if test true-form & false-form]]
  (str "if (" (emit test env) ") { \n"
       (emit true-form env)
       "\n }"
       (when (first false-form)
         (str " else { \n"
              (emit (first false-form) env)
              " }"))))

(defn emit-aget [env var idxs]
  (apply str
         (emit var env)
         (interleave (repeat "[") (emit-args env idxs) (repeat "]"))))

(defmethod emit-special 'aget [type env [_aget var & idxs]]
  (emit-aget env var idxs))

(defmethod emit-special 'dot-method [_type env [method obj & args]]
  (let [method-str (rstr/drop 1 (str method))]
    (if (str/starts-with? method-str "-")
      (emit-aget env obj [(subs method-str 1)])
      (emit-method env obj (symbol method-str) args))))

;; TODO: this should not be reachable in user space
(defmethod emit-special 'return [_type env [_return expr]]
  (statement (str "return " (emit (assoc env :context :expr) env))))

#_(defmethod emit-special 'delete [type [return expr]]
    (str "delete " (emit expr)))

(defmethod emit-special 'set! [_type env [_set! var val & more]]
  (assert (or (nil? more) (even? (count more))))
  (str (emit var) " = " (emit val env) statement-separator
       (when more (str (emit (cons 'set! more) env)))))

(defmethod emit-special 'new [_type env [_new class & args]]
  (str "new " (emit class env) (comma-list (emit-args env args))))

(defmethod emit-special 'inc! [_type env [_inc var]]
  (str (emit var env) "++"))

(defmethod emit-special 'dec! [_type env [_dec var]]
  (str (emit var env) "--"))

(defmethod emit-special 'dec [_type env [_ var]]
  (str "(" (emit var env) " - " 1 ")"))

(defmethod emit-special 'inc [_type env [_ var]]
  (str "(" (emit var env) " + " 1 ")"))

(defmethod emit-special 'defined? [_type env [_ var]]
  (str "typeof " (emit var env) " !== \"undefined\" && " (emit var env) " !== null"))

(defmethod emit-special '? [_type env [_ test then else]]
  (str (emit test env) " ? " (emit then env) " : " (emit else env)))

(defmethod emit-special 'and [_type env [_ & more]]
  (apply str (interpose "&&" (emit-args env more))))

(defmethod emit-special 'or [_type env [_ & more]]
  (apply str (interpose "||" (emit-args env more))))

(defmethod emit-special 'quote [_type _env [_ & more]]
  (apply str more))

(defn emit-do [env exprs]
  (let [bl (butlast exprs)
        l (last exprs)
        ctx (:context env)
        statement-env (assoc env :context :statement)
        iife? (and (seq bl) (= :expr ctx))]
    (let [s (cond-> (str (str/join "" (map #(statement (emit % statement-env)) bl))
                         (emit l (assoc env :context
                                        (if iife? :return
                                            ctx))))
              iife?
              (wrap-iife))]
      ;;(prn exprs '-> s)
      s)))

(defmethod emit-special 'do [_type env [_ & exprs]]
  (emit-do env exprs))

(defmethod emit-special 'while [_type env [_while test & body]]
  (str "while (" (emit test) ") { \n"
       (emit-do env body)
       "\n }"))

;; TODO: re-implement
(defmethod emit-special 'doseq [_type env [_doseq bindings & body]]
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
  (let [body (emit-do (assoc env :context :return) body)]
    (str (when-not elide-function?
           (str (when *async*
                  "async ") "function ")) (comma-list sig) " {\n"
         body "\n}")))

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
  (let [expanded (core-fn expr sigs)]
    (emit expanded env)))

(defmethod emit-special 'defn [_type env [fn name & args :as expr]]
  (let [;;async (:async (meta name))
        [_def _name _fn-expr :as expanded] (core-defn expr {} name args)]
    ;; (prn fn-expr (meta fn-expr))
    (emit expanded env)))

(defmethod emit-special 'try [_type env [_try & body :as expression]]
  (let [try-body (remove #(contains? #{'catch 'finally} (first %))
                         body)
        catch-clause (filter #(= 'catch (first %))
                             body)
        finally-clause (filter #(= 'finally (first %))
                               body)]
    (cond
      (and (empty? catch-clause)
           (empty? finally-clause))
      (throw (new Exception (str "Must supply a catch or finally clause (or both) in a try statement! " expression)))

      (> (count catch-clause) 1)
      (throw (new Exception (str "Multiple catch clauses in a try statement are not currently supported! " expression)))

      (> (count finally-clause) 1)
      (throw (new Exception (str "Cannot supply more than one finally clause in a try statement! " expression)))

      :true (str "try{\n"
                 (emit-do env try-body)
                 "}\n"
                 (if-let [[_ exception & catch-body] (first catch-clause)]
                   (str "catch(" (emit env exception) "){\n"
                        (emit-do env catch-body)
                        "}\n"))
                 (if-let [[_ & finally-body] (first finally-clause)]
                   (str "finally{\n"
                        (emit-do env finally-body)
                        "}\n"))))))

#_(defmethod emit-special 'break [_type _env [_break]]
    (statement "break"))

(derive #?(:clj clojure.lang.Cons :cljs Cons) ::list)
(derive #?(:clj clojure.lang.IPersistentList :cljs IList) ::list)
#?(:cljs (derive List ::list))

(defmethod emit ::list [expr env]
  (if (symbol? (first expr))
    (let [head (first expr)]
      (cond
        (and (= (rstr/get (str head) 0) \.)
             (> (count (str head)) 1)

             (not (= (rstr/get (str head) 1) \.))) (emit-special 'dot-method env expr)
        (contains? built-in-macros head) (let [macro (built-in-macros head)]
                                           (emit (apply macro expr {} (rest expr)) env))
        (special-form? head) (emit-special head env expr)
        (infix-operator? head) (emit-infix head env expr)
        (prefix-unary? head) (emit-prefix-unary head expr)
        (suffix-unary? head) (emit-suffix-unary head expr)
        :else (emit-special 'funcall env expr)))
    (if (list? expr)
      (emit-special 'funcall env expr)
      (throw (new Exception (str "invalid form: " expr))))))

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

(defmethod emit #?(:clj clojure.lang.LazySeq
                   :cljs LazySeq) [expr env]
  (emit (into [] expr) env))

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
           (format "{ %s }" keys))
         (emit-wrap env))))

(defn transpile-form [f]
  (emit f {:context :statement}))

(defn transpile-string* [s]
  (let [rdr (e/reader s)]
    (loop [transpiled ""]
      (let [next-form (e/parse-next rdr {:readers {'js #(vary-meta % assoc ::js true)}})]
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

(defn transpile-file [{:keys [in-file out-file]}]
  (let [out-file (or out-file
                     (str/replace in-file #".cljs$" ".mjs"))
        transpiled (transpile-string (slurp in-file))]
    (spit out-file transpiled)
    {:out-file out-file}))

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
