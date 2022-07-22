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
   [cherry.internal.destructure :refer [core-let]]
   [cherry.internal.fn :refer [core-defn core-fn]]
   #?(:clj [cherry.resource :as resource])
   [clojure.string :as str]
   [com.reasonr.string :as rstr]
   [edamame.core :as e])
  #?(:cljs (:require-macros [cherry.resource :as resource])))

#?(:cljs (def Exception js/Error))

#?(:cljs (def format gstring/format))

(defn- throwf [& message]
  (throw (Exception. (apply format message))))

(defmulti emit (fn [ expr ] (type expr)))

(defmulti emit-special (fn [ & args] (first args)))

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

(defmethod emit nil [expr]
  "null")

(defmethod emit #?(:clj java.lang.Integer :cljs js/Number) [expr]
  (str expr))

#?(:clj (defmethod emit clojure.lang.Ratio [expr]
         (str (float expr))))

(defmethod emit #?(:clj java.lang.String :cljs js/String) [^String expr]
  (str \" (.replace expr "\"" "\\\"") \"))


(defmethod emit #?(:clj clojure.lang.Keyword :cljs Keyword) [expr]
  #_(when-not (valid-symbol? (name expr))
    (#'throwf "%s is not a valid javascript symbol" expr))
  (swap! *imported-core-vars* conj 'keyword)
  (str (format "keyword(%s)" (pr-str (subs (str expr) 1)))))

(defn munge* [expr]
  (let [munged (str (munge expr))]
    (cond-> munged
      (and (str/ends-with? munged "$")
           (not= (str expr) "default"))
      (str/replace #"\$$" ""))))

(defmethod emit #?(:clj clojure.lang.Symbol :cljs Symbol) [expr]
  (let [expr-ns (namespace expr)
        js? (= "js" expr-ns)
        expr-ns (when-not js? expr-ns)
        expr (str expr-ns (when expr-ns
                                     ".")
                  (munge* (name expr)))]
    #_(when-not (valid-symbol? (str expr))
      (#' throwf "%s is not a valid javascript symbol" expr))
    (str expr)))

#?(:clj (defmethod emit #?(:clj java.util.regex.Pattern) [expr]
          (str \/ expr \/)))

(defmethod emit :default [expr]
  ;; RegExp case moved here:
  ;; References to the global RegExp object prevents optimization of regular expressions.
  #?(:cljs (if (instance? js/RegExp expr)
             (str \/ expr \/)
             (str expr))
     :clj (str expr)))

(def special-forms (set ['var '. '.. 'if 'funcall 'fn 'fn* 'quote 'set!
                         'return 'delete 'new 'do 'aget 'while 'doseq
                         'inc! 'dec! 'dec 'inc 'defined? 'and 'or
                         '? 'try 'break
                         'await 'const 'defn 'let 'let* 'ns 'def]))

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

(defn emit-prefix-unary [type [operator arg]]
  (str operator (emit arg)))

(defn emit-suffix-unary [type [operator arg]]
  (str (emit arg) operator))

(defn emit-infix [type [operator & args]]
  (when (and (not (chainable-infix-operators operator)) (> (count args) 2))
    (throw (Exception. (str "operator " operator " supports only 2 arguments"))))
  (let [substitutions {'= '=== '!= '!== 'not= '!==}]
    (str "(" (str/join (str " " (or (substitutions operator) operator) " ")
                       (map emit args)) ")")))

(def ^{:dynamic true} var-declarations nil)

(defmacro with-var-declarations [& body]
  `(binding [var-declarations (atom [])]
     ~@body))

(defmethod emit-special 'var [type [var & more]]
  (apply swap! var-declarations conj (filter identity (map (fn [name i] (when (odd? i) name)) more (iterate inc 1))))
  (apply str (interleave (map (fn [[name expr]]
                                (str (when-not var-declarations "var ") (emit name) " = " (emit expr)))
                              (partition 2 more))
                         (repeat statement-separator))))

(defn emit-const [more]
  (apply str (interleave (map (fn [[name expr]]
                                (str "const " (emit name) " = " (emit expr)))
                              (partition 2 more))
                         (repeat statement-separator))))

(defmethod emit-special 'const [_type [_const & more]]
  (emit-const more))

(defmethod emit-special 'def [_type [_const & more]]
  (let [name (first more)]
    (swap! *public-vars* conj (munge* name))
    (emit-const more)))

(declare emit-do)

(defn wrap-await [s]
  (format "(%s)" (str "await " s)))

(defmethod emit-special 'await [_ [_await more]]
  (wrap-await (emit more)))

(defn wrap-iife [s]
  (cond-> (format "(%sfunction () {\n %s\n})()" (if *async* "async " "") s)
    *async* (wrap-await)))

(defn return [s]
  (format "return %s;" s))

(defmethod emit-special 'let* [type [_let bindings & more]]
  (let [partitioned (partition 2 bindings)]
    (wrap-iife
     (str
      (let [names (distinct (map (fn [[name _]]
                                   name)
                                 partitioned))]
        (statement (str "let " (str/join ", " names))))
      (apply str (interleave (map (fn [[name expr]]
                                    (str (emit name) " = " (emit expr)))
                                  partitioned)
                             (repeat statement-separator)))
      (return (emit-do more))))))

(defmethod emit-special 'let [type [_let bindings & more]]
  (emit (core-let bindings more))
  #_(prn (core-let bindings more)))

(defn process-require-clause [[libname & {:keys [refer as]}]]
  (str (when as
         (statement (format "import * as %s from '%s'" as libname)))
       (when refer
         (statement (format "import { %s } from '%s'"  (str/join ", " refer) libname)))))

(defmethod emit-special 'ns [_type [_ns _name & clauses]]
  (reduce (fn [acc [k & exprs]]
            (if (= :require k)
              (str acc (str/join "" (map process-require-clause exprs)))
              acc))
          ""
          clauses
          ))

(defmethod emit-special 'funcall [_type [name & args :as expr]]
  (if (and (symbol? name)
           (= "cljs.core" (namespace name)))
    (emit (with-meta (list* (symbol (clojure.core/name name)) args)
            (meta expr)))
    (str (if (and (list? name) (= 'fn (first name))) ; function literal call
           (str "(" (emit name) ")")
           (let [name
                 (if (contains? core-vars name)
                   (let [name (symbol (munge* name))]
                     (swap! *imported-core-vars* conj name)
                     name)
                   name)]
             (emit name)))
         (comma-list (map emit args)))))

(defmethod emit-special 'str [type [str & args]]
  (apply clojure.core/str (interpose " + " (map emit args))))

(defn emit-method [obj method args]
  (str (emit obj) "." (emit method) (comma-list (map emit args))))

(defmethod emit-special '. [type [period obj method & args]]
  (emit-method obj method args))

(defmethod emit-special '.. [type [dotdot & args]]
  (apply str (interpose "." (map emit args))))

(defmethod emit-special 'if [type [if test true-form & false-form]]
  (str "if (" (emit test) ") { \n"
       (emit true-form)
       "\n }"
       (when (first false-form)
         (str " else { \n"
              (emit (first false-form))
              " }"))))

(defn emit-aget [var idxs]
  (apply str
         (emit var)
         (interleave (repeat "[") (map emit idxs) (repeat "]"))))

(defmethod emit-special 'aget [type [_aget var & idxs]]
  (emit-aget var idxs))

(defmethod emit-special 'dot-method [type [method obj & args]]
  (let [method-str (rstr/drop 1 (str method))]
    (if (str/starts-with? method-str "-")
      (emit-aget obj [(subs method-str 1)])
      (emit-method obj (symbol method-str) args))))

(defmethod emit-special 'return [type [return expr]]
  (statement (str "return " (emit expr))))

(defmethod emit-special 'delete [type [return expr]]
  (str "delete " (emit expr)))

(defmethod emit-special 'set! [type [set! var val & more]]
  (assert (or (nil? more) (even? (count more))))
  (str (emit var) " = " (emit val) statement-separator
       (if more (str (emit (cons 'set! more))))))

(defmethod emit-special 'new [type [new class & args]]
  (str "new " (emit class) (comma-list (map emit args))))

(defmethod emit-special 'inc! [type [inc var]]
  (str (emit var) "++"))

(defmethod emit-special 'dec! [type [dec var]]
  (str (emit var) "--"))

(defmethod emit-special 'dec [type [_ var]]
  (str "(" (emit var) " - " 1 ")"))

(defmethod emit-special 'inc [type [_ var]]
  (str "(" (emit var) " + " 1 ")"))

(defmethod emit-special 'defined? [type [_ var]]
  (str "typeof " (emit var) " !== \"undefined\" && " (emit var) " !== null"))

(defmethod emit-special '? [type [_ test then else]]
  (str (emit test) " ? " (emit then) " : " (emit else)))

(defmethod emit-special 'and [type [_ & more]]
  (apply str (interpose "&&" (map emit more))))

(defmethod emit-special 'or [type [_ & more]]
  (apply str (interpose "||" (map emit more))))

(defmethod emit-special 'quote [type [_ & more]]
  (apply str more))

(defn emit-do [exprs & [{:keys [top-level?]
                         }]]
  (let [bl (butlast exprs)
        l (last exprs)]
    (cond-> (str (str/join "" (map (comp statement emit) bl))
                 (cond-> (emit l)
                   (not top-level?)
                   (return)))
      (not top-level?) (wrap-iife))))

(defmethod emit-special 'do [type [ do & exprs]]
  (emit-do exprs))

(defmethod emit-special 'while [type [while test & body]]
  (str "while (" (emit test) ") { \n"
       (emit-do body)
       "\n }"))

(defmethod emit-special 'doseq [type [doseq bindings & body]]
  (str "for (" (emit (first bindings)) " in " (emit (second bindings)) ") { \n"
       (if-let [more (nnext bindings)]
         (emit (list* 'doseq more body))
         (emit-do body))
       "\n }"))

(defn emit-var-declarations []
  #_(when-not (empty? @var-declarations)
    (apply str "var "
           (str/join ", " (map emit @var-declarations))
           statement-separator)))

(declare emit-function*)

(defn emit-function [name sig body & [elide-function?]]
  (assert (or (symbol? name) (nil? name)))
  (assert (vector? sig))
  (let [body (return (emit-do body))]
    (str (when-not elide-function?
           (str (when *async*
                  "async ") "function ")) (comma-list sig) " {\n"
         #_(emit-var-declarations) body "\n}")))

(defn emit-function* [expr]
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
             (emit-function name signature body true)))
      (let [signature (first expr)
            body (rest expr)]
        (str (emit-function nil signature body))))))

(defmethod emit-special 'fn* [type [fn & sigs :as expr]]
  (let [async? (:async (meta expr))]
    (binding [*async* async?]
      (emit-function* sigs))))

(defmethod emit-special 'fn [type [fn & sigs :as expr]]
  (let [expanded (core-fn expr sigs)]
    (emit expanded)))

(defmethod emit-special 'defn [type [fn name & args :as expr]]
  (let [;;async (:async (meta name))
        [_def _name _fn-expr :as expanded] (core-defn expr {} name args)]
    ;; (prn fn-expr (meta fn-expr))
    (emit expanded)))

(defmethod emit-special 'try [type [try & body :as expression]]
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
                 (emit-do try-body)
                 "}\n"
                 (if-let [[_ exception & catch-body] (first catch-clause)]
                   (str "catch(" (emit exception) "){\n"
                        (emit-do catch-body)
                        "}\n"))
                 (if-let [[_ & finally-body] (first finally-clause)]
                   (str "finally{\n"
                        (emit-do finally-body)
                        "}\n"))))))

(defmethod emit-special 'break [type [break]]
  (statement "break"))

(derive #?(:clj clojure.lang.Cons :cljs Cons) ::list)
(derive #?(:clj clojure.lang.IPersistentList :cljs IList) ::list)
#?(:cljs (derive List ::list))

(defmethod emit ::list [expr]
  (if (symbol? (first expr))
    (let [head (first expr)]
      (cond
        (and (= (rstr/get (str head) 0) \.)
             (> (count (str head)) 1)

             (not (= (rstr/get (str head) 1) \.))) (emit-special 'dot-method expr)
        (special-form? head) (emit-special head expr)
        (infix-operator? head) (emit-infix head expr)
        (prefix-unary? head) (emit-prefix-unary head expr)
        (suffix-unary? head) (emit-suffix-unary head expr)
        :else (emit-special 'funcall expr)))
    (if (list? expr)
      (emit-special 'funcall expr)
      (throw (new Exception (str "invalid form: " expr))))))

#?(:cljs (derive PersistentVector ::vector))

(defmethod emit #?(:clj clojure.lang.IPersistentVector
                   :cljs ::vector) [expr]
  (swap! *imported-core-vars* conj 'vector)
  (format "vector(%s)" (str/join ", " (map emit expr))))

(defmethod emit #?(:clj clojure.lang.LazySeq
                   :cljs LazySeq) [expr]
  (emit (into [] expr)))

#?(:cljs (derive PersistentArrayMap ::map))
#?(:cljs (derive PersistentHashMap ::map))

(defmethod emit #?(:clj clojure.lang.IPersistentMap
                   :cljs ::map) [expr]
  (let [map-fn
        (if (::js (meta expr))
          'js_obj
          (if (<= (count expr) 8)
            'arrayMap
            'hashMap))
        key-fn (if (= map-fn 'js_obj)
                  name identity)]
    (swap! *imported-core-vars* conj map-fn)
    (letfn [(mk-pair [pair] (str (emit (key-fn (key pair))) ", " (emit (val pair))))]
      (format "%s(%s)" map-fn (str/join ", " (map mk-pair (seq expr)))))))

(defn transpile-string [s]
  (let [rdr (e/reader s)]
    (loop [transpiled ""]
      (let [next-form (e/parse-next rdr {:readers {'js #(vary-meta % assoc ::js true)}})]
        (if (= ::e/eof next-form)
          transpiled
          (let [next-t (emit next-form)
                next-js (some-> next-t not-empty (statement))]
            (recur (str transpiled next-js))))))))

#?(:cljs
   (defn slurp [f]
     (fs/readFileSync f "utf-8")))

#?(:cljs
   (defn spit [f s]
     (fs/writeFileSync f s "utf-8")))

(defn transpile-file [{:keys [in-file out-file]}]
  (let [core-vars (atom #{})
        public-vars (atom #{})]
    (binding [*imported-core-vars* core-vars
              *public-vars* public-vars]
      (let [out-file (or out-file
                         (str/replace in-file #".cljs$" ".mjs"))
            transpiled (transpile-string (slurp in-file))
            transpiled (if-let [core-vars (seq @core-vars)]
                         (str (format "import { %s } from 'cherry-cljs/cljs.core.js'\n"
                                      (str/join ", " core-vars))
                              transpiled)
                         transpiled)
            transpiled (str transpiled
                            (format "\nexport { %s }\n"
                                    (str/join ", " (disj @public-vars "default$")))
                            (when (contains? @public-vars "default$")
                              "export default default$\n"))]
        (spit out-file transpiled)
        {:out-file out-file}))))
