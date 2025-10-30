(ns squint.compiler-common
  (:refer-clojure :exclude [*target* *repl*])
  (:require
   #?(:cljs [goog.string :as gstring])
   #?(:cljs [goog.string.format])
   [clojure.string :as str]
   [squint.compiler.utils :as utils]
   [squint.defclass :as defclass]
   [squint.internal.macros :as macros]))

(def ^:dynamic *aliases* (atom {}))
(def ^:dynamic *async* false)
(def ^:dynamic *imported-vars* (atom {}))
(def ^:dynamic *excluded-core-vars* (atom #{}))
(def ^:dynamic *public-vars* (atom #{}))
(def ^:dynamic *recur-targets* (atom []))
(def ^:dynamic *repl* false)
(def ^:dynamic *cljs-ns* 'user)
(def ^:dynamic *target* :squint)

(def common-macros
  {'coercive-boolean macros/coercive-boolean
   'coercive-= macros/coercive-=
   'coercive-not= macros/coercive-not=
   'coercive-not macros/coercive-not
   'bit-not macros/bit-not
   'bit-and macros/bit-and
   'unsafe-bit-and macros/unsafe-bit-and
   'bit-or macros/bit-or
   'int macros/int
   'bit-xor macros/bit-xor
   'bit-and-not macros/bit-and-not
   'bit-clear macros/bit-clear
   'bit-flip macros/bit-flip
   'bit-test macros/bit-test
   'bit-shift-left macros/bit-shift-left
   'bit-shift-right macros/bit-shift-right
   'bit-shift-right-zero-fill macros/bit-shift-right-zero-fill
   'unsigned-bit-shift-right macros/unsigned-bit-shift-right
   'bit-set macros/bit-set
   'undefined? macros/undefined?
   'str macros/stringify
   '= macros/equals
   'not= macros/core-not=
   'identical? macros/core-identical?})

(defn wrap-parens [s]
  (str "(" s ")"))

#?(:cljs (def Exception js/Error))

#?(:cljs (def format gstring/format))

(def emit utils/emit)

(defmulti emit-special (fn [disp _env & _args] disp))

(defn emit-return [s env]
  (if (= :return (:context env))
    (format "return %s"
            s)
    s))

(defrecord Code [js tag transient]
  Object
  (toString [_] js))

(defn tagged-expr
  ([js tag]
   (map->Code {:js js
               :tag tag}))
  ([js tag transient]
   (map->Code {:js js
               :tag tag
               :transient transient})))

(defmethod emit-special 'js* [_ env [_js* template & args :as expr]]
  (let [mexpr (meta expr)
        tag (:tag mexpr)
        transient (:transient mexpr)
        template (str template)]
    (cond->
        (-> (reduce (fn [template substitution]
                      (str/replace-first template "~{}"
                                         (emit substitution (merge (assoc env :context :expr)))))
                    template
                    args)
            (emit-return (merge env (meta expr))))
      tag (tagged-expr tag transient))))

(defn expr-env [env]
  (assoc env :context :expr :top-level false))

(defn yield-iife
  [s env]
  (if (:gen env)
    (format "yield* (%s)" s)
    s))

(defn wrap-await
  [s _env]
  (format "(%s)" (str "await " s)))

(defn wrap-implicit-iife
  [s env]
  (let [gen? (:gen env)]
    (cond-> (format (if gen?
                      "(%sfunction%s () {\n%s\n})()"
                      "(%s() =>%s {\n%s\n})()")
                    (if *async* "async " "")
                    (if gen?
                      "*" "")
                    s)
      *async* (wrap-await env)
      true (yield-iife env))))

(defmethod emit-special 'throw [_ env [_ expr]]
  (cond-> (str "throw " (emit expr (expr-env env)))
    (= :expr (:context env)) (wrap-implicit-iife env)))

(def statement-separator ";\n")

(defn str-tail
  "Returns the last n characters of s."
  ^String [n js]
  (let [s (str js)]
    (if (< (count s) n)
      s
      (.substring s (- (count s) n)))))

(defn statement [expr]
  (let [expr (str expr)]
    (when-not (str/blank? expr)
      (if (not (= statement-separator (str-tail (count statement-separator) expr)))
        (str expr statement-separator)
        expr))))

(defn comma-list [coll]
  (str "(" (str/join ", " coll) ")"))

(defn munge* [expr]
  (let [munged (str (munge expr))
        keep #{"import" "await"}]
    (cond-> munged
      (and (str/ends-with? munged "$")
           (contains? keep (str expr)))
      (str/replace #"\$$" ""))))

(defn munge**
  "Same as munge but does not do any renaming of reserved words"
  [x]
  (let [munged (str (munge x))
        #?@(:cljs [js? (#'js-reserved? (str x))])]
    #?(:cljs (if js? (subs munged 0 (dec (count munged))) munged)
       :clj munged)))

(defmethod emit nil [_ env]
  (when-not (= :statement (:context env))
    (emit-return "null" env)))

#?(:clj (derive #?(:clj java.lang.Integer) ::number))
#?(:clj (derive #?(:clj java.lang.Long) ::number))
#?(:cljs (derive js/Number ::number))

(defn escape-jsx [expr env]
  (let [html? (:html env)]
    (if (and (:jsx env) (or html?
                            (not (:jsx-runtime env))))
      (do
        (when html?
          (when-let [dyn (:has-dynamic-expr env)]
            (reset! dyn true)))
        (format "%s{%s}"
                (if html?
                  "$"
                  "")
                expr))
      expr)))

(defmethod emit ::number [expr env]
  (-> (str expr)
      (emit-return env)
      (escape-jsx env)
      (tagged-expr 'number)))

(defmethod emit #?(:clj java.lang.String :cljs js/String) [^String expr env]
  (let [unsafe-html? (:unsafe-html env)]
    (cond-> (if (and (:jsx env)
                     (not (:jsx-attr env))
                     (or (:html env)
                         (not (:jsx-runtime env)))
                     (not unsafe-html?))
              (str/replace expr #"([<>])" (fn [x]
                                            (get
                                             {"<" "&lt;"
                                              ">" "&gt;"} (second x))))
              (emit-return (cond-> expr
                             (not (:skip-quotes env))
                             (pr-str)) env))
      (pos? (count expr)) (tagged-expr 'string))))

(defmethod emit #?(:clj java.lang.Boolean :cljs js/Boolean) [^String expr env]
  (-> (if (:jsx-attr env)
        (escape-jsx expr env)
        (str expr))
      (emit-return env)
      (tagged-expr 'boolean)))

#?(:clj (defmethod emit #?(:clj java.util.regex.Pattern) [expr _env]
          (str \/ expr \/)))

(defmethod emit :default [expr env]
  ;; RegExp case moved here:
  ;; References to the global RegExp object prevents optimization of regular expressions.
  (emit-return (str expr) env))

(def prefix-unary-operators '#{!})

(def suffix-unary-operators '#{++ --})

(def infix-operators #{"+" "+=" "-" "-=" "/" "*" "%" "==" "===" "<" ">" "<=" ">=" "!="
                       "<<" ">>" "<<<" ">>>" "!==" "&" "|" "&&" "||" "instanceof"
                       "bit-or" "bit-and" "js-mod" "js-??"})

(def boolean-infix-operators
  #{"==" "===" "<" ">" "<=" ">=" "!=" "instanceof"})

(def chainable-infix-operators #{"+" "-" "*" "/" "&" "|" "&&" "||" "bit-or" "bit-and" "js-??"})

(defn infix-operator? [env expr]
  (contains? (or (:infix-operators env)
                 infix-operators)
             (name expr)))

(defn prefix-unary? [expr]
  (contains? prefix-unary-operators expr))

(defn suffix-unary? [expr]
  (contains? suffix-unary-operators expr))

(defn emit-args [env args]
  (let [env (assoc env :context :expr :top-level false)]
    (map #(emit % env) args)))

(defn emit-infix [_type enc-env [operator & args]]
  (let [env (assoc enc-env :context :expr :top-level false)
        acount (count args)
        op-name (name operator)
        bool? (contains? boolean-infix-operators op-name)]
    (if (and (not (chainable-infix-operators op-name)) (> acount 2))
      (emit (list 'cljs.core/&&
                  (list operator (first args) (second args))
                  (list* operator (rest args)))
            enc-env)
      (->
       (cond (and (= '- operator)
                  (= 1 acount))
             (str "-" (emit (first args) env))
             (and (= '/ operator)
                  (= 1 acount))
             (str "1 / " (emit (first args) env))
             :else
             (-> (let [substitutions {'== "===" '!= "!=="
                                      '+ "+"
                                      'bit-or "|"
                                      'bit-and "&"
                                      'js-mod "%"
                                      'js-?? "??"}]
                   (str/join (str " " (or (substitutions operator)
                                          operator) " ")
                             (emit-args env args)))))
       wrap-parens
       (emit-return enc-env)
       (cond->
           bool? (tagged-expr 'boolean))))))

(def core-vars (atom #{}))

(def ^:dynamic *core-package* "squint-cljs/core.js")

(defn maybe-core-var [sym env]
  (let [m (munge sym)]
    (when (and (contains? (:core-vars env) m)
               (not (contains? @*excluded-core-vars* m)))
      (swap! *imported-vars* update *core-package* (fnil conj #{}) m)
      (str
       (when-let [core-alias (:core-alias env)]
         (str core-alias "."))
       m))))

(defn alias-munge [s]
  (-> s str munge (str/replace #"\." "_DOT_") ))

(defmethod emit #?(:clj clojure.lang.Symbol :cljs Symbol) [expr env]
  (if (:quote env)
    (emit-return (escape-jsx (emit (list 'cljs.core/symbol
                                         (str expr))
                                   (dissoc env :quote)) env)
                 env)
    (let [ns-state @(:ns-state env)
          current (:current ns-state)
          current-ns (get ns-state current)
          aliases (:aliases current-ns)]
      (if (and (simple-symbol? expr)
               (not (contains? aliases expr))
               (str/includes? (str expr) "."))
        (let [[fname path] (str/split (str expr) #"\." 2)
              fname (symbol fname)]
          (emit-return (escape-jsx (str (emit fname (dissoc (expr-env env) :jsx))
                                        "." (munge** path)) env)
                       env))
        (let [munged-name (fn [expr] (munge* (name expr)))
              expr (if-let [sym-ns (some-> (namespace expr) munge)]
                     (let [sn (symbol (name expr))]
                       (or (when (or (= "cljs.core" sym-ns)
                                     (= "clojure.core" sym-ns))
                             (some-> (maybe-core-var sn env) munge))
                           (when (= "js" sym-ns)
                             (munge* (name expr)))
                           (when-let [resolved-ns (get (:aliases env) (symbol sym-ns))]
                             (str (if (symbol? resolved-ns)
                                    (munge resolved-ns)
                                    sym-ns)
                                  "."
                                  (munged-name sn)))
                           (when (contains? aliases (symbol sym-ns))
                             (str
                              (when *repl*
                                (str "globalThis." (munge *cljs-ns*) "."))
                              (alias-munge sym-ns) "."
                              (munged-name sn)))
                           (let [ns (namespace expr)
                                 munged (munge ns)
                                 nm (name expr)]
                             (if (and *repl* (not= "Math" munged))
                               (let [ns-state (some-> env :ns-state deref)]
                                 (if (get-in ns-state [(symbol ns) (symbol nm)])
                                   (str (munge ns) "." (munge nm))
                                   (str "globalThis." (munge *cljs-ns*) "." munged "." (munge nm))))
                               (str munged "." (munge (name expr)))))))
                     (if-let [renamed (get (:var->ident env) expr)]
                       (let [tag (:tag (meta renamed))]
                         (cond-> (munge** (str renamed))
                           tag (tagged-expr tag)))
                       (let [alias (get aliases expr)]
                         (or
                          (when (contains? current-ns expr)
                            (str (when *repl*
                                   (str "globalThis." (munge *cljs-ns*) ".")) (munged-name expr)))
                          (when (contains? (:refers current-ns) expr)
                            (str (when *repl*
                                   (str "globalThis." (munge *cljs-ns*) "."))
                                 (munged-name expr)))
                          (some-> (maybe-core-var expr env) munge)
                          (when alias
                            (str (when *repl*
                                   (str "globalThis." (munge *cljs-ns*) "."))
                                 (alias-munge expr)))
                          (let [m (munged-name expr)]
                            m)))))]
          (emit-return (escape-jsx expr env)
                       env))))))

(defn save-pragma [env next-t]
  (let [p (:pragmas env)
        past (and p (:past @p))]
    (if (and (:top-level env)
             (re-find #"^(/\*|//|\"|\')" (str next-t)))
      (let [js (str next-t "\n")]
        (if (and p (not past)
                 ;; always leave jsdoc untouched
                 (not (str/starts-with? js "/*")))
          (do (swap! p update :js str js)
              nil)
          js))
      (let [js (if (= :statement (:context env))
                 (statement next-t)
                 next-t)]
        (if (or (not p) past) js
            (do
              (swap! p assoc :past true)
              js))))))

(defn emit-do [env exprs]
  (let [bl (butlast exprs)
        l (last exprs)
        ctx (:context env)
        statement-env (assoc env :context :statement)
        iife? (and (seq bl) (= :expr ctx))
        exprs (str/join (map #(save-pragma statement-env (emit % statement-env)) bl))
        lctx (if iife? :return ctx)
        res (emit l (assoc env :context lctx))
        tag (:tag res)
        transient (:transient res)
        res (cond-> res
              (= :return ctx) (statement))
        s (cond-> (str exprs res)
            iife?
            (wrap-implicit-iife env))]
    (cond-> s
      tag (tagged-expr tag transient))))

(defmethod emit-special 'do [_type env [_ & exprs]]
  (emit-do env exprs))

(defn emit-let [enc-env bindings body loop?]
  (let [gensym (:gensym enc-env)
        context (:context enc-env)
        env (assoc enc-env :context :expr)
        partitioned (partition 2 bindings)
        iife? (or (= :expr context)
                  (:top-level env))
        upper-var->ident (:var->ident enc-env)
        [bindings var->ident]
        (let [env (dissoc env :top-level)]
          (reduce (fn [[acc var->ident] [var-name rhs]]
                    (let [vm (meta var-name)
                          rename? (not (:squint.compiler/no-rename vm))
                          renamed (if rename? (munge (gensym var-name))
                                      (munge var-name))
                          lhs (str renamed)
                          rhs (emit rhs (assoc env :var->ident var->ident))
                          tag (:tag rhs)
                          expr (format "%s %s = %s;\n" (if loop? "let" "const")lhs rhs)
                          var->ident
                          (-> var->ident
                              (assoc var-name
                                     (cond-> renamed
                                       tag
                                       (vary-meta assoc :tag tag))))]
                      [(str acc expr) var->ident]))
                  ["" upper-var->ident]
                  partitioned))
        enc-env (assoc enc-env :var->ident var->ident :top-level false)
        body (binding [*recur-targets*
                       (if loop? (map var->ident (map first partitioned))
                           *recur-targets*)]
               (emit-do (if iife?
                          (assoc enc-env :context :return)
                          enc-env) body))
        tag (:tag body)
        transient (:transient body)]
    (cond-> (str
             bindings
             (when loop?
               "while(true){\n")
             ;; TODO: move this to env arg?
             body
             (when loop?
               ;; TODO: not sure why I had to insert the ; here, but else
               ;; (loop [x 1] (+ 1 2 x)) breaks
               ";break;\n}\n"))
      iife?
      (wrap-implicit-iife env)
      iife?
      (emit-return enc-env)
      tag (tagged-expr tag transient))))

(defmethod emit-special 'let* [_type enc-env [_let bindings & body]]
  (emit-let enc-env bindings body false))

(defmethod emit-special 'loop* [_ env [_ bindings & body]]
  (emit-let env bindings body true))

(defmethod emit-special 'case* [_ env [_ v tests thens default]]
  (let [gensym (:gensym env)
        expr? (= :expr (:context env))
        gs (gensym "caseval__")
        eenv (expr-env env)
        ret (cond-> (str
                     (when expr?
                       (str "var " gs ";\n"))
                     "switch (" (emit v eenv) ") {"
                     (str/join (map (fn [test then]
                                      (str/join
                                       (map (fn [test]
                                              (str "case " (emit test eenv) ":\n"
                                                   (if expr?
                                                     (str gs " = " then)
                                                     (statement (emit then env)))
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
              expr? (wrap-implicit-iife env))]
    ret))

(defmethod emit-special 'recur [_ env [_ & exprs]]
  (let [gensym (:gensym env)
        bindings *recur-targets*
        temps (repeatedly (count exprs) gensym)
        eenv (expr-env env)]
    (when-let [cb (:recur-callback env)]
      (cb bindings))
    (str
     (str/join ""
               (map (fn [temp expr]
                      (statement (format "let %s = %s"
                                         temp (emit expr eenv))))
                    temps exprs))
     (str/join ""
               (map (fn [binding temp]
                      (statement (format "%s = %s"
                                         binding temp)))
                    bindings temps))
     "continue;\n")))

(defn no-top-level [env]
  (dissoc env :top-level))

(defn emit-var [[name ?doc ?expr :as expr] _skip-var? env]
  (let [expr (if (= 3 (count expr))
               ?expr ?doc)
        env* (no-top-level env)]
    (str "var " (munge name) " = "
         (emit expr (expr-env env*)) ";\n"
         (when *repl*
           (emit-return (str "globalThis."
                            (when *cljs-ns*
                              (str (munge *cljs-ns*) "."))
                            (munge name) " = " (munge name)
                            (when (= :statement (:context env)) ";\n"))
                        env)))))

(defmethod emit-special 'def [_type env [_const & more :as expr]]
  (let [name (first more)]
    ;; TODO: move *public-vars* to :ns-state atom
    (when-not (:private (meta name))
      (swap! *public-vars* conj (munge* name)))
    (swap! (:ns-state env) (fn [state]
                             (let [current (:current state)]
                               (assoc-in state [current name] {}))))
    (let [skip-var? (:squint.compiler/skip-var (meta expr))]
      (emit-var more skip-var? env))))

(defn js-await [env more]
  (emit-return
   (wrap-await (emit more (expr-env env)) env)
   env))

(defmethod emit-special 'js/await [_ env [_await more]]
  (js-await env more))

(defmethod emit-special 'js-await [_ env [_await more]]
  (js-await env more))

#_(defn wrap-iife [s]
    (cond-> (format "(%sfunction () {\n %s\n})()" (if *async* "async " "") s)
      *async* (wrap-await)))

(defn resolve-import-map [import-maps lib]
  (get import-maps lib lib))

(defn resolve-ns [env alias]
  (let [import-maps (:import-maps env)]
    (case *target*
      :squint
      (case alias
        (squint.string clojure.string) (resolve-import-map import-maps "squint-cljs/src/squint/string.js")
        (squint.set clojure.set) (resolve-import-map import-maps "squint-cljs/src/squint/set.js")
        (if (symbol? alias)
          (if-let [resolve-ns (:resolve-ns env)]
            (or (resolve-ns alias)
                alias)
            alias)
          (resolve-import-map import-maps alias)))
      :cherry
      (case alias
        (cljs.string clojure.string) "cherry-cljs/lib/clojure.string.js"
        (cljs.walk clojure.walk) "cherry-cljs/lib/clojure.walk.js"
        (cljs.set clojure.set) "cherry-cljs/lib/clojure.set.js"
        (cljs.pprint clojure.pprint) "cherry-cljs/lib/cljs.pprint.js"
        alias)
      alias)))

(defn unwrap [s]
  (str/replace (str s) #"^\(|\)$" ""))

(defn process-require-clause [env current-ns-name [libname & {:keys [rename refer as with]}]]
  (when-not (or (= 'squint.core libname)
                (= 'cherry.core libname))
    (let [env (expr-env env)
          libname (resolve-ns env libname)
          [libname suffix] (str/split (if (string? libname) libname (str libname)) #"\$" 2)
          default? (= "default" suffix) ;; we only support a default suffix for now anyway
          as (when as (munge as))
          expr (str
                (when (and as default?)
                  (if *repl*
                    (statement (format "const %s = (await import('%s'%s)).%s"
                                       (alias-munge as)
                                       libname
                                       (if with
                                         (str ", " (emit {:with with} env))
                                         "")
                                       suffix))
                    (statement (format "import %s from '%s'%s" as libname
                                       (if with
                                         (str " with " (unwrap (emit with env)))
                                         "")))))
                (when (and (not as) (not refer))
                  ;; import presumably for side effects
                  (if *repl*
                    (statement (format "await import('%s'%s)" libname
                                       (if with
                                         (str ", " (emit {:with with} env))
                                         "")))
                    (statement (format "import '%s'%s" libname
                                       (if with
                                         (str " with " (unwrap (emit with env)))
                                         "")))))
                (when (and as (not default?))
                  (swap! *imported-vars* update libname (fnil identity #{}))
                  (statement (if *repl*
                               (format "var %s = await import('%s'%s)" (alias-munge as) libname
                                       (if with
                                         (str ", " (emit {:with with} env))
                                         ""))
                               (format "import * as %s from '%s'%s" (alias-munge as) libname
                                       (if with
                                         (str " with " (unwrap (emit with env)))
                                         "")))))
                (when refer
                  (swap! (:ns-state env)
                         (fn [ns-state]
                           (let [current (:current ns-state)]
                             (update-in ns-state [current :refers]
                                        (fn [refers]
                                          (merge refers (zipmap (map (fn [refer]
                                                                       (get rename refer refer)) refer) (repeat libname))))))))
                  (let [referred+renamed (str/join ", "
                                                   (map (fn [refer]
                                                          (str (munge refer)
                                                               (when-let [renamed (get rename refer)]
                                                                 (str " as " (munge renamed)))))
                                                        refer))]
                    (if *repl*
                      (str (statement (format "var { %s } = (await import ('%s'%s))%s" (str/replace referred+renamed " as " ": ") libname
                                              (if with
                                                (str ", " (emit {:with with} env))
                                                "")
                                              (if suffix
                                                (str "." suffix)
                                                "")))
                           (str/join (map (fn [sym]
                                            (let [sym (munge sym)]
                                              (statement (str "globalThis." (munge current-ns-name) "." sym " = " sym))))
                                          (map (fn [refer]
                                                 (get rename refer refer))
                                               refer))))

                      (if default?
                        (let [libname* ((:gensym env) "default")]
                          (str (statement (format "import %s from '%s'%s" libname* libname (if with
                                                                                             (str " with " (unwrap (emit with env)))
                                                                                             "")))
                               (statement (format "const { %s } = %s" referred+renamed libname*))))
                        (statement (format "import { %s } from '%s'%s" referred+renamed libname (if with
                                                                                                  (str " with " (unwrap (emit with env)))
                                                                                                  ""))))))))]
      (when as
        (swap! (:ns-state env)
               (fn [ns-state]
                 (let [current (:current ns-state)]
                   (update-in ns-state [current :aliases] (fn [aliases]
                                                            ((fnil assoc {}) aliases as libname)))))))
      (when-not (:elide-imports env)
        expr)
      #_nil)))

(defn ensure-global [mname]
  (let [split-name (str/split (str mname) #"\.")]
    (-> (reduce (fn [{:keys [js nk]} k]
                  (let [nk (str (when nk
                                  (str nk ".")) k)]
                    {:js (str js "globalThis." nk " = globalThis." nk " || {};\n")
                     :nk nk}))
                {}
                split-name)
        :js)))

(defmethod emit-special 'ns [_type env [_ns name & clauses]]
  (let [mname (munge name)
        ensure-obj (ensure-global mname)
        ns-obj (str "globalThis." mname)]
    ;; TODO: deprecate *cljs-ns*
    (set! *cljs-ns* name)
    (swap! (:ns-state env) assoc :current name)
    (reset! *aliases*
            (->> clauses
                 (some
                  (fn [[k & exprs]]
                    (when (= :require k) exprs)))
                 (reduce
                  (fn [aliases [full as alias]]
                    (let [full (resolve-ns env full)]
                      (case as
                        (:as :as-alias)
                        (assoc aliases (munge alias) full)
                        #_:else
                        aliases)))
                  {:current name
                   (:core-alias env) *core-package*})))
    (str
     (when *repl*
       ensure-obj)
     (reduce (fn [acc [k & exprs]]
               (cond
                 (= :require k)
                 (str acc (str/join "" (map #(process-require-clause env name %) exprs)))
                 (= :refer-clojure k)
                 (let [{:keys [exclude]} exprs]
                   (swap! *excluded-core-vars* into exclude)
                   acc)
                 :else acc))
             ""
             clauses)
     (when *repl*
       (str
        (reduce-kv (fn [acc k _v]
                     (if (symbol? k)
                       (str acc
                            ns-obj "." (alias-munge k) " = " (alias-munge k) ";\n")
                       acc))
                   ""
                   @*aliases*))))))

(defmethod emit-special 'require [_ env [_ & clauses]]
  (let [clauses (map second clauses)]
    (reset! *aliases*
            (->> clauses
                 (reduce
                  (fn [aliases [full as alias]]
                    (let [full (resolve-ns env full)]
                       (case as
                        (:as :as-alias)
                        (assoc aliases alias full)
                        aliases)))
                  {:current name})))
    (str (str/join "" (map #(process-require-clause env *cljs-ns* %) clauses))
         (when *repl*
           (let [mname (munge *cljs-ns*)
                 split-name (str/split (str mname) #"\.")
                 ensure-obj (-> (reduce (fn [{:keys [js nk]} k]
                                          (let [nk (str (when nk
                                                          (str nk ".")) k)]
                                            {:js (str js "globalThis." nk " = globalThis." nk " || {};\n")
                                             :nk nk}))
                                        {}
                                        split-name)
                                :js)
                 ns-obj (str "globalThis." mname)]
             (str
              ensure-obj
              #_#_ns-obj " = {aliases: {}};\n"
              (reduce-kv (fn [acc k _v]
                           (if (symbol? k)
                             (let [k (munge k)]
                               (str acc
                                    ns-obj "." #_".aliases." k " = " k ";\n"))
                             acc))
                         ""
                         @*aliases*)))))))

(defmethod emit-special 'str [_type env [_str & args]]
  (apply clojure.core/str (interpose " + " (emit-args env args))))

(defn emit-method [env obj method args]
  (let [eenv (expr-env env)
        method (munge** method)]
    (emit-return (str (emit obj eenv) "."
                      method
                      (comma-list (emit-args env args)))
                 env)))

(defmethod emit-special '. [_type env [_period obj method & args]]
  (let [[method args] (if (seq? method)
                        [(first method) (rest method)]
                        [method args])
        method-str (str method)]
    (if (str/starts-with? method-str "-")
      (emit (list 'js* (str "~{}." (symbol (munge** (subs method-str 1)))) obj) env)
      (emit-method env obj (symbol method-str) args))))

(defn emit-aget [env var idxs]
  (emit-return (apply str
                      (emit var (expr-env env))
                      (interleave (repeat "[") (emit-args env idxs) (repeat "]")))
               env))

(defmethod emit-special 'aget [_type env [_aget var & idxs]]
  (emit-aget env var idxs))

(defn emit-aset [env var idxs]
  (let [v (last idxs)
        idxs (butlast idxs)
        last-idx (last idxs)
        idxs (butlast idxs)]
    (emit (list 'clojure.core/unchecked-set
                (list* 'clojure.core/aget var idxs)
                last-idx
                v) env)))

(defmethod emit-special 'aset [_type env [_aset var & idxs]]
  (emit-aset env var idxs))

;; TODO: this should not be reachable in user space
(defmethod emit-special 'return [_type env [_return _expr]]
  (statement (str "return " (emit (assoc env :context :expr) env))))

#_(defmethod emit-special 'delete [type [return expr]]
    (str "delete " (emit expr)))

(defmethod emit-special 'set! [_type env [_set! target val alt :as expr]]
  (let [[target val]
        (if (= 4 (count expr))
          [`(. ~target ~val) alt]
          [target val])
        eenv (expr-env env)]
    (emit-return (str (emit target eenv) " = " (emit val eenv))
                 env)))

(defmethod emit-special 'new [_type env [_new class & args]]
  (emit-return (str "new " (emit class (expr-env env)) (comma-list (emit-args env args))) env))

(defmethod emit-special 'dec [_type env [_ var]]
  (emit-return (str "(" (emit var (assoc env :context :expr)) " - " 1 ")") env))

(defmethod emit-special 'inc [_type env [_ var]]
  (emit-return (str "(" (emit var (assoc env :context :expr)) " + " 1 ")") env))

#_(defmethod emit-special 'defined? [_type env [_ var]]
    (str "typeof " (emit var env) " !== \"undefined\" && " (emit var env) " !== null"))

#_(defmethod emit-special '? [_type env [_ test then else]]
    (str (emit test env) " ? " (emit then env) " : " (emit else env)))

#_#_(defmethod emit-special 'and [_type env [_ & more]]
      (if (empty? more)
        true
        (emit-return (wrap-parens (apply str (interpose " && " (emit-args env more)))) env)))

(defmethod emit-special 'or [_type env [_ & more]]
  (if (empty? more)
    nil
    (emit-return (wrap-parens (apply str (interpose " || " (emit-args env more)))) env)))

(defmethod emit-special 'while [_type env [_while test & body]]
  (str "while (" (emit test (expr-env env)) ") { \n"
       (emit-do env body)
       "\n}"))

(defn map-params [m]
  (let [ks (:keys m)]
    (zipmap ks (map munge ks))))

(defn gen-param [param gensym]
  (let [tag (:tag (meta param))]
    (cond-> (munge (gensym param))
      tag (with-meta {:tag tag}))))

(defn ->sig [env sig]
  (let [gensym (:gensym env)]
    (reduce (fn [[env sig seen] param]
              (if (map? param)
                (let [params (map-params param)]
                  [(update env :var->ident (fn [m]
                                             (-> m
                                                 (merge params))))
                   (conj sig param)
                   (into seen params)])
                (if (contains? seen param)
                  (let [new-param (gen-param param gensym)
                        env (update env :var->ident (fn [m]
                                                      (->
                                                       m
                                                       (assoc param new-param))))
                        sig (conj sig new-param)
                        seen (conj seen param)]
                    [env sig seen])
                  (let [new-param (gen-param param identity)]
                    [(update env :var->ident (fn [m]
                                               (-> m
                                                   (assoc param new-param))))
                     (conj sig new-param)
                     (conj seen param)]))))
            [env [] #{}]
            sig)))

(defn destructured-map [_env x]
  (let [keys (:keys x)]
    (str "{" (str/join "," (map munge keys)) "}")))

(defn emit-function [env _name sig body & [elide-function?]]
  ;; (assert (or (symbol? name) (nil? name)))
  (assert (vector? sig))
  (let [arrow? (:arrow env)
        single-expr-arrow? (and arrow? (= 1 (count body)))
        [env sig] (->sig env sig)]
    (binding [*recur-targets* sig]
      (let [recur? (volatile! nil)
            env (assoc env :recur-callback
                       (fn [coll]
                         (when (identical? sig coll)
                           (vreset! recur? true))))
            body (if single-expr-arrow?
                   (emit (first body) (assoc env :context :expr))
                   (emit-do (assoc env :context :return)
                            body))
            body (if @recur?
                   (format "while(true){
%s
break;}" body)
                   body)]
        (str (when-not elide-function?
               (str (when *async*
                      "async ")
                    (when-not arrow? "function")
                    (when (:gen env)
                      "*")
                    (when (or (not arrow?)
                              *async*)
                      " ")))
             (comma-list (map (fn [x]
                                (if (map? x)
                                  (destructured-map env x)
                                  x)) sig))
             (when arrow?
               "=>")
             (if single-expr-arrow?
               body
               (str
                " {\n"
                body "\n}")))))))

(defn emit-function* [env expr opts]
  (let [name (when (symbol? (first expr)) (first expr))
        expr (if name (rest expr) expr)
        expr (if (seq? (first expr))
               ;; TODO: multi-arity:
               (first expr)
               expr)
        signature (first expr)
        arrow? (or (:arrow env) (:=> (meta signature)))
        env (assoc env :arrow arrow?)
        omit? (and (= :statement (:context env))
                   (nil? name))]
    (when-not omit?
      (if (some #(= '& %) signature)
        ;; this still needs macro-expansion, see issue #599
        (let [new-f (with-meta
                      (cons 'fn expr)
                      (meta expr))]
          (emit new-f env))
        (-> (if name
              (let [body (rest expr)]
                (str (when *async*
                       "async ") "function"
                     ;; TODO: why is this duplicated here and in emit-function?
                     (when (:gen env)
                       "*")
                     " "
                     (munge name) " "
                     (emit-function env name signature body true)))
              (let [body (rest expr)]
                (str (emit-function env nil signature body))))
            (cond-> (and
                     (not (:squint.internal.fn/def opts))
                     (= :expr (:context env))) (wrap-parens))
            (emit-return env))))))

(defmethod emit-special 'fn* [_type env [_fn & sigs :as expr]]
  (let [m (meta expr)
        async? (:async m)
        gen? (:gen m)
        env (assoc env :gen gen?)
        arrow? (:=> m)
        env (assoc env :arrow arrow?)]
    (binding [*async* async?]
      (emit-function* env sigs (meta expr)))))

(defmethod emit-special 'try [_type env [_try & body :as expression]]
  (let [gensym (:gensym env)
        try-body (remove #(contains? #{'catch 'finally} (and (seq? %)
                                                             (first %)))
                         body)
        catch-clause (filter #(= 'catch (and (seq? %)
                                             (first %)))
                             body)
        finally-clause (filter #(= 'finally (and (seq? %)
                                                 (first %)))
                               body)
        non-statement? (not= :statement (:context env))
        outer-env env
        env (if non-statement?
              (assoc env :context :return)
              env)]
    (cond
      (and (empty? catch-clause)
           (empty? finally-clause))
      (throw (new Exception (str "Must supply a catch or finally clause (or both) in a try statement! " expression)))

      (> (count catch-clause) 1)
      (throw (new Exception (str "Multiple catch clauses in a try statement are not currently supported! " expression)))

      (> (count finally-clause) 1)
      (throw (new Exception (str "Cannot supply more than one finally clause in a try statement! " expression)))

      :else
      (-> (cond-> (str "try{\n"
                       (emit-do env try-body)
                       "}\n"
                       (when-let [[_ _exception binding & catch-body] (first catch-clause)]
                         (let [binding (munge binding)
                               env (update env :var->ident (fn [m]
                                                             (-> m
                                                                 (assoc binding (gensym binding)))))]
                           (str "catch(" (emit binding (expr-env env)) "){\n"
                                (emit-do env catch-body)
                                "}\n")))
                       (when-let [[_ & finally-body] (first finally-clause)]
                         (str "finally{\n"
                              (emit-do (assoc env :context :statement) finally-body)
                              "}\n")))
            (not= :statement (:context env))
            (wrap-implicit-iife env))
          (emit-return outer-env)))))

(defmethod emit-special 'funcall [_type env [fname & args :as expr]]
  (let [ns (when (symbol? fname) (namespace fname))
        fname (if ns (symbol (munge ns) (name fname))
                  fname)
        cherry? (= :cherry *target*)
        cherry+interop? (and
                         cherry?
                         (= "js" ns))
        tag (:tag (meta expr))
        transient (:transient (meta expr))]
    (cond-> (emit-return (str
                         (emit fname (expr-env env))
                         ;; this is needed when calling keywords, symbols, etc. We could
                         ;; optimize this later by inferring that we're not directly
                         ;; calling a `function`.
                         (when (and cherry? (not cherry+interop?)) ".call")
                         (comma-list (emit-args env
                                                (if cherry?
                                                  (if (not cherry+interop?)
                                                    (cons
                                                     (if (= "super" (first (str/split (str fname) #"\.")))
                                                       'self__ nil) args)
                                                    args)
                                                  args))))
                         env)
      tag (tagged-expr tag transient))))

(defmethod emit-special 'letfn* [_ env [_ form & body]]
  (let [gensym (:gensym env)
        bindings (take-nth 2 form)
        fns (take-nth 2 (rest form))
        binding-map (zipmap bindings (map #(gensym %) bindings))
        env (update env :var->ident (fn [m]
                                      (-> m
                                          (merge binding-map))))
        bindings (map #(vary-meta (get binding-map %) assoc :squint.compiler/no-rename true) bindings)
        form (interleave bindings fns)
        let `(let* ~(vec form) ~@body)]
    (emit let env)))

(defmethod emit-special 'zero? [_ env [_ num]]
  (tagged-expr (-> (format "(%s === 0)" (emit num (assoc env :context :expr)))
                   (emit-return env))
               'boolean))

(defmethod emit-special 'neg? [_ env [_ num]]
  (tagged-expr (-> (format "(%s < 0)" (emit num (assoc env :context :expr)))
                   (emit-return env))
               'boolean))

(defmethod emit-special 'pos? [_ env [_ num]]
  (tagged-expr (-> (format "(%s > 0)" (emit num (assoc env :context :expr)))
                   (emit-return env))
               'boolean))

(defmethod emit-special 'nil? [_ env [_ obj]]
  (tagged-expr (-> (format "(%s == null)" (emit obj (assoc env :context :expr)))
                   (emit-return env))
               'boolean))

(defmethod emit-special 'js-in [_ env [_ key obj]]
  (tagged-expr (emit (list 'js* "~{} in ~{}" key obj) env)
               'boolean))

(defmethod emit-special 'js-yield [_ env [_ key obj]]
  (emit (list 'js* "yield ~{}" key obj) env))

(defmethod emit-special 'js-yield* [_ env [_ key obj]]
  (emit (list 'js* "yield* ~{}" key obj) env))

(defmethod emit #?(:clj clojure.lang.MapEntry :cljs MapEntry) [expr env]
  ;; RegExp case moved here:
  ;; References to the global RegExp object prevents optimization of regular expressions.
  (emit (vec expr) env))

(def special-forms '#{zero? pos? neg? js-delete nil? js-in js-yield
                      js-yield*})

(derive #?(:clj clojure.lang.Cons :cljs Cons) ::list)
(derive #?(:clj clojure.lang.IPersistentList :cljs IList) ::list)
(derive #?(:clj clojure.lang.LazySeq :cljs LazySeq) ::list)
#?(:cljs (derive List ::list))
(derive #?(:bb (class (list))
           :clj clojure.lang.PersistentList$EmptyList
           :cljs EmptyList) ::empty-list)

(defmethod emit ::list [expr env]
  ((-> env :emit ::list) expr env))

(defmethod emit ::empty-list [_expr env]
  (emit '(clojure.core/list) (dissoc env :quote)))

#?(:cljs (derive PersistentVector ::vector))

(defmethod emit #?(:clj clojure.lang.IPersistentVector
                   :cljs ::vector) [expr env]
  ((-> env :emit ::vector) expr env))

#?(:cljs (derive PersistentArrayMap ::map))
#?(:cljs (derive PersistentHashMap ::map))

(defmethod emit #?(:clj clojure.lang.IPersistentMap
                   :cljs ::map) [expr env]
  (let [f (-> env :emit ::map)]
    (f expr env)))

(defmethod emit #?(:clj clojure.lang.Keyword :cljs Keyword)
  [expr env]
  (let [f (-> env :emit ::keyword)]
    (f expr env)))

(defmethod emit #?(:clj clojure.lang.PersistentHashSet
                   :cljs PersistentHashSet)
  [expr env]
  (let [f (-> env :emit ::set)]
    (f expr env)))

(defmethod emit-special 'quote [_ env [_ form]]
  (emit-return (emit form (expr-env (assoc env :quote true))) env))

(defmethod emit-special 'js-delete [_ env [_ obj key]]
  (emit-return (emit (list 'js* "delete ~{}[~{}]" obj key) env) env))

(defmethod emit-special :default [sym env expr]
  (let [f (-> env :emit ::special)]
    (f sym env expr)))

(defn skip-truth? [tag]
  (contains? #{'boolean 'string} tag))

(defmethod emit-special 'if [_type env [_if test then else :as expr]]
  ;; NOTE: I tried making the output smaller if the if is in return position
  ;; if .. return .. else return ..
  ;; => return ( .. ? : ..);
  ;; but this caused issues with recur in return position:
  ;; (defn foo [x] (if x 1 (recur (dec x)))) We might fix this another time, but
  ;; tools like eslint will rewrite in the short form anyway.
  (let [expr-env (assoc env :context :expr)
        naked-condition (emit test expr-env)
        skip-truth? (or (skip-truth? (:tag naked-condition))
                        (skip-truth? (:tag (meta expr)))
                        (skip-truth? (:tag (meta test))))
        condition (if skip-truth?
                    naked-condition
                    (emit (list 'clojure.core/truth_ (list 'js* naked-condition)) expr-env))]
    (if (= :expr (:context env))
      (->
       ;; NOTE: we wrap the entire expression in parens here because some macros like bitshift-left expect their args to be already wrapped in parens
       ;; So far we've taken the approach that at the location where arguments are used we wrap those in parens which is a bit contradictory
       ;; At some point we may want to clean this up a bit. See #622 as well.
       (format "((%s) ? (%s) : (%s))"
               condition
               (emit then env)
               (emit else env))
       (emit-return env))
      (str (format "if (%s) {\n" condition)
           (emit then env)
           "}"
           (when (= 4 (count expr)) ;; explicit else branch
             (str " else {\n"
                  (emit else env)
                  "}"))))))

(defn wrap-double-quotes [x]
  (str \" x \"))

(defn emit-css
  [v env]
  (if (contains? v :&)
    (let [rest-opts (dissoc v :&)
          env (assoc env :js true)
          cherry? (= :cherry *target*)]
      (when-let [dyn (:has-dynamic-expr env)]
        (reset! dyn true))
      (-> (format "${squint_html.css(%s,%s)}"
                  (emit (cond->> (get v :&)
                          cherry? (list `clj->js)) (dissoc env :jsx))
                  (emit (cond->> rest-opts
                          cherry? (list `clj->js)) (dissoc env :jsx)))
          (wrap-double-quotes)))
    (let [env (assoc env :html-attr true)]
      (-> (reduce
           (fn [acc [k v]]
             (str acc
                  (emit k env) ":"
                  (emit v env) ";"))
           ""
           v)
          (wrap-double-quotes)))))

(defn jsx-attrs [v env]
  (let [env (expr-env env)
        html? (:html env)]
    (if (and (not html?)
             (:jsx-runtime env))
      (when v
        (emit v (dissoc env :jsx)))
      (if (seq v)
        (let [v* v]
          (str
           " "
           (if (and html? (contains? v* :&))
             (let [rest-opts (dissoc v* :&)]
               (when-let [dyn (:has-dynamic-expr env)]
                 (reset! dyn true))
               (let [env (assoc env :js true)
                     cherry? (= :cherry *target*)]
                 (format "${squint_html.attrs(%s,%s)}"
                         (emit (cond->> (get v* :&)
                                 cherry? (list `clj->js)) (dissoc env :jsx))
                         (emit (cond->> rest-opts
                                 cherry? (list `clj->js)) (dissoc env :jsx)))))
             (str/join " "
                       (map
                        (fn [[k v]]
                          (let [str? (or (string? v)
                                         (when (= :squint *target*)
                                           (keyword? v)))]
                            (if (= :& k)
                              (str "{..." (emit v (dissoc env :jsx)) "}")
                              (str (name k) "="
                                   (let [env env]
                                     (cond
                                       (and html? (map? v))
                                       (emit-css v env)
                                       #_#_(and html? (vector? v))
                                       (-> (str/join " " (map #(emit % env) v))
                                           (wrap-double-quotes))
                                       :else
                                       (cond-> (emit v (assoc env :jsx false))
                                         (not str?)
                                         ;; since we escape here, we
                                         ;; can probably remove
                                         ;; escaping elsewhere?
                                         (escape-jsx (assoc env :html-attr (and html? (not str?))))
                                         (and html? (not str?))
                                         (wrap-double-quotes))))))))
                        v)))))
        ""))))

(defmethod emit-special 'squint.defclass/defclass* [_ env form]
  (let [name (second form)]
    (swap! *public-vars* conj (munge* name))
    (defclass/emit-class env
      emit
      (fn [async body-fn]
        (binding [*async* async]
          (body-fn)))
      emit-return
      form)))

(defmethod emit-special 'squint.defclass/super* [_ env form]
  (defclass/emit-super env emit (second form)))

(def ^{:doc "A list of elements that must be rendered without a closing tag. From hiccup."
       :private true}
  void-tags
  #{"area" "base" "br" "col" "command" "embed" "hr" "img" "input" "keygen" "link"
    "meta" "param" "source" "track" "wbr"})

(defn void-tag? [tag-name]
  (contains? void-tags tag-name))

(defn- parse-tag
  "From hiccup, thanks @weavejester"
  [^String tag]
  (let [id-index    (let [index (.indexOf tag "#")] (when (pos? index) index))
        class-index (let [index (.indexOf tag ".")] (when (pos? index) index))]
    [(cond
       id-index    (.substring tag 0 id-index)
       class-index (.substring tag 0 class-index)
       :else tag)
     (when id-index
       (if class-index
         (.substring tag (unchecked-inc-int id-index) class-index)
         (.substring tag (unchecked-inc-int id-index))))
     (when class-index
       (.substring tag (unchecked-inc-int class-index)))]))

(defn- merge-attrs [attrs short-attrs]
  (let [attrs (if-let [c (:class attrs)]
                (if-let [sc (:class short-attrs)]
                  (assoc attrs :class (str sc " " c))
                  attrs)
                attrs)
        attrs (if-let [id (:id short-attrs (:id attrs))]
                (assoc attrs :id id)
                attrs)]
    attrs))

(defn emit-vector [expr env]
  (if (and (:jsx env)
           (let [f (first expr)]
             (or (keyword? f)
                 (symbol? f))))
    (let [need-html-import (:need-html-import env)
          has-dynamic-expr? (or (:has-dynamic-expr env) (atom false))
          env (assoc env :has-dynamic-expr has-dynamic-expr?)
          v expr
          tag (first v)
          keyw? (keyword? tag)
          attrs (second v)
          tag-name (symbol tag)
          unsafe-html? (= '$ tag-name)
          attrs (when (and (map? attrs) (not unsafe-html?)) attrs)
          elts (if attrs (nnext v) (next v))
          fragment? (= '<> tag-name)
          tag-name* (if fragment?
                      (symbol "")
                      tag-name)
          [tag-name id class]
          (if (and (not fragment?) keyw?)
            (parse-tag  (subs (str tag) 1))
            [(emit tag-name* (expr-env (dissoc env :jsx)))])
          classes (when class (str/replace class "." " "))
          short-attrs (cond-> nil
                        classes (assoc :class classes)
                        id (assoc :id id))
          attrs (if attrs
                  (merge-attrs attrs short-attrs)
                  short-attrs)
          html? (:html env)
          outer-html? (:outer-html (meta expr))]
      (when unsafe-html? (reset! has-dynamic-expr? true))
      (if (and (not html?) (:jsx env) (:jsx-runtime env))
        (let [single-child? (= 1 (count elts))]
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
        (let [expr-env* (assoc (expr-env env) :unsafe-html unsafe-html?)
              ret (str
                   (cond (and html? (or fragment?
                                        unsafe-html?))
                         ""
                         :else (str "<"
                                    tag-name
                                    (jsx-attrs attrs env)
                                    ">"))
                   (let [ret (str/join ""
                                       (map (fn [elt]
                                              (if (and unsafe-html? (string? elt))
                                                (let [expr-env* (assoc expr-env* :skip-quotes true)]
                                                  (emit elt expr-env*))
                                                (emit elt expr-env*)))
                                            elts))]
                     ret)
               (if (and html? (or fragment?
                                  unsafe-html?
                                  (void-tag? tag-name)))
                 ""
                 (str "</" tag-name ">")))]
          (when outer-html?
            (when need-html-import
              (reset! need-html-import true)))
          (emit-return
           (cond->> ret
             outer-html?
             (format "%s`%s`"
                     (if-let [t (:tag (meta expr))]
                       (emit t (dissoc expr-env* :jsx :html))
                       (if
                         @has-dynamic-expr? "squint_html.tag"
                         "squint_html.html")))
             unsafe-html?
             (format "${%s`%s`}"
                     "squint_html.unsafe_tag"))
           env))))
    (emit-return
     (if (and (= :cherry *target*)
              (not (::js (meta expr))))
       (format "%svector(%s)"
               (if-let [core-alias (:core-alias env)]
                 (str core-alias ".")
                 "")
               (str/join ", " (emit-args env expr)))
       (format "[%s]"
               (str/join ", " (emit-args env expr))))
     env)))

(defmethod emit-special 'squint-compiler-html [_ env [_ form]]
  (let [env (assoc env :html true :jsx true)
        form (vary-meta form assoc :outer-html true)]
    (emit form env)))

(defmethod emit-special 'deftype* [_ env [_ t fields pmasks body]]
  (let [fields* (map munge fields)]
    (str "var " (munge t)
         " = "
         (format "function %s {
%s
%s
};
%s"
                 (comma-list fields*)
                 (str/join "\n"
                           (map (fn [fld]
                                  (str "this." fld " = " fld ";"))
                                fields*))
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
                           (-> vi
                               (merge
                                (zipmap fields
                                        (map (fn [fld]
                                               (symbol (str "self__." fld)))
                                             fields*))))))
                        (assoc :type true)))))))
