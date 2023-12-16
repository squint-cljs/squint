(ns squint.compiler-common
  (:refer-clojure :exclude [*target* *repl*])
  (:require
   #?(:cljs [goog.string :as gstring])
   #?(:cljs [goog.string.format])
   [clojure.string :as str]
   [squint.internal.macros :as macros]))

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
   'undefined? macros/undefined?})

#?(:cljs (def Exception js/Error))

#?(:cljs (def format gstring/format))

(defmulti emit (fn [expr _env] (type expr)))

(defmulti emit-special (fn [disp _env & _args] disp))

(defn emit-return [s env]
  (if (= :return (:context env))
    (format "return %s" s)
    s))

(defrecord Code [js bool]
  Object
  (toString [_] js))

(defn bool-expr [js]
  (map->Code {:js js
              :bool true}))

(defmethod emit-special 'js* [_ env [_js* & opts :as expr]]
  (let [bool? (= 'boolean (:tag (meta expr)))
        [env' template substitutions] (if (map? (first opts))
                                        [(first opts)
                                         (second opts)
                                         (drop 2 opts)]
                                        [nil (first opts) (rest opts)])]
    (cond->
        (-> (reduce (fn [template substitution]
                      (str/replace-first template "~{}"
                                         (emit substitution (merge (assoc env :context :expr) env'))))
                    template
                    substitutions)
            (emit-return (merge env (meta expr))))
      bool? bool-expr)))

(defn expr-env [env]
  (assoc env :context :expr :top-level false))

(defmethod emit-special 'throw [_ env [_ expr]]
  (str "throw " (emit expr (expr-env env))))

(def statement-separator ";\n")

(def ^:dynamic *aliases* (atom {}))
(def ^:dynamic *async* false)
(def ^:dynamic *imported-vars* (atom {}))
(def ^:dynamic *excluded-core-vars* (atom #{}))
(def ^:dynamic *public-vars* (atom #{}))
(def ^:dynamic *recur-targets* (atom []))
(def ^:dynamic *repl* false)
(def ^:dynamic *cljs-ns* 'user)
(def ^:dynamic *target* :squint)

(defn str-tail
  "Returns the last n characters of s."
  ^String [n js]
  (let [s (str js)]
    (if (< (count s) n)
      s
      (.substring s (- (count s) n)))))

(defn statement [expr]
  (if (not (= statement-separator (str-tail (count statement-separator) expr)))
    (str expr statement-separator)
    expr))

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
  (emit-return "null" env))

#?(:clj (derive #?(:clj java.lang.Integer) ::number))
#?(:clj (derive #?(:clj java.lang.Long) ::number))
#?(:cljs (derive js/Number ::number))

(defn escape-jsx [expr env]
  (if (and (:jsx env) (not (:jsx-runtime env)))
    (format "{%s}" expr)
    expr))

(defmethod emit ::number [expr env]
  (-> (str expr)
      (emit-return env)
      (escape-jsx env)))

(defmethod emit #?(:clj java.lang.String :cljs js/String) [^String expr env]
  (cond-> (if (and (:jsx env)
                   (not (:jsx-attr env))
                   (not (:jsx-runtime env)))
            expr
            (emit-return (pr-str expr) env))
    (pos? (count expr)) (bool-expr)))

(defmethod emit #?(:clj java.lang.Boolean :cljs js/Boolean) [^String expr env]
  (-> (if (:jsx-attr env)
        (escape-jsx expr env)
        (str expr))
      (emit-return env)))

#?(:clj (defmethod emit #?(:clj java.util.regex.Pattern) [expr _env]
          (str \/ expr \/)))

(defmethod emit :default [expr env]
  ;; RegExp case moved here:
  ;; References to the global RegExp object prevents optimization of regular expressions.
  (emit-return (str expr) env))

(def prefix-unary-operators '#{!})

(def suffix-unary-operators '#{++ --})

(def infix-operators #{"+" "+=" "-" "-=" "/" "*" "%" "=" "==" "===" "<" ">" "<=" ">=" "!="
                       "<<" ">>" "<<<" ">>>" "!==" "&" "|" "&&" "||" "not=" "instanceof"
                       "bit-or" "bit-and" "js-mod"})

(def boolean-infix-operators
  #{"=" "==" "===" "<" ">" "<=" ">=" "!=" "not=" "instanceof"})

(def chainable-infix-operators #{"+" "-" "*" "/" "&" "|" "&&" "||" "bit-or" "bit-and"})

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
            env)
      (if (and (= '- operator)
               (= 1 acount))
        (str "-" (emit (first args) env))
        (-> (let [substitutions {'= "===" == "===" '!= "!=="
                                 'not= "!=="
                                 '+ "+"
                                 'bit-or "|"
                                 'bit-and "&"
                                 'js-mod "%"}]
              (str "(" (str/join (str " " (or (substitutions operator)
                                              operator) " ")
                                 (emit-args env args)) ")"))
            (emit-return enc-env)
            (cond-> bool? (bool-expr)))))))

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

(defmethod emit #?(:clj clojure.lang.Symbol :cljs Symbol) [expr env]
  (if (:quote env)
    (emit-return (escape-jsx (emit (list 'cljs.core/symbol
                                         (str expr))
                                   (dissoc env :quote)) env)
                 env)
    (if (and (simple-symbol? expr)
             (str/includes? (str expr) "."))
      (let [[fname path] (str/split (str expr) #"\." 2)
            fname (symbol fname)]
        (escape-jsx (str (emit fname (dissoc (expr-env env) :jsx))
                         "." (munge** path)) env))
      (let [munged-name (fn [expr] (munge* (name expr)))
            expr (if-let [sym-ns (some-> (namespace expr) munge)]
                   (let [sn (symbol (name expr))]
                     (or (when (or (= "cljs.core" sym-ns)
                                   (= "clojure.core" sym-ns))
                           (some-> (maybe-core-var sn env) munge))
                         (when (= "js" sym-ns)
                           (munge* (name expr)))
                         (when-let [resolved-ns (get @*aliases* (symbol sym-ns))]
                           (str (if (symbol? resolved-ns)
                                  (munge resolved-ns)
                                  sym-ns) "." #_#_sym-ns "_" (munged-name sn)))
                         (let [munged (munge (namespace expr))]
                           (if (and *repl* (not= "Math" munged))
                             (str "globalThis." (munge *cljs-ns*) "." munged "." (munge (name expr)))
                             (str munged "." (munge (name expr)))))))
                   (if-let [renamed (get (:var->ident env) expr)]
                     (cond-> (munge** (str renamed))
                       (:bool (meta renamed)) (bool-expr))
                     (let [ns-state @(:ns-state env)
                           current (:current ns-state)
                           current-ns (get ns-state current)
                           m (munged-name expr)]
                       (or
                        (when (contains? current-ns expr)
                          (str (when *repl*
                                 (str "globalThis." (munge *cljs-ns*) ".")) m))
                        (some-> (maybe-core-var expr env) munge)
                        (when (or (contains? (:refers current-ns) expr)
                                  (let [alias (get (:aliases current-ns) expr)]
                                    alias))
                          (str (when *repl*
                                 (str "globalThis." (munge *cljs-ns*) ".")) m))
                        (let [m (munged-name expr)]
                          m)))))]
        (emit-return (escape-jsx expr env)
                     env)))))

(defn wrap-await
  ([s] (wrap-await s false))
  ([s _return?]
   (format "(%s)" (str "await " s))))

(defn wrap-iife
  ([s] (wrap-iife s false))
  ([s return?]
   (cond-> (format "(%sfunction () {\n %s\n})()" (if *async* "async " "") s)
     *async* (wrap-await return?))))

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
      (let [js (statement next-t)]
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
        s (cond-> (str (str/join "" (map #(save-pragma env (emit % statement-env)) bl))
                       (emit l (assoc env :context
                                      (if iife? :return
                                          ctx))))
            iife?
            (wrap-iife))]
    s))

(defmethod emit-special 'do [_type env [_ & exprs]]
  (emit-do env exprs))

(defn emit-let [enc-env bindings body is-loop]
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
                                      var-name)
                          lhs (str renamed)
                          rhs (emit rhs (assoc env :var->ident var->ident))
                          rhs-bool? (:bool rhs)
                          expr (format "let %s = %s;\n" lhs rhs)
                          var->ident (assoc var->ident var-name
                                            (vary-meta renamed
                                                       assoc :bool rhs-bool?))]
                      [(str acc expr) var->ident]))
                  ["" upper-var->ident]
                  partitioned))
        enc-env (assoc enc-env :var->ident var->ident :top-level false)]
    (cond-> (str
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
      iife?
      (wrap-iife)
      iife?
      (emit-return enc-env))))

(defmethod emit-special 'let* [_type enc-env [_let bindings & body]]
  (emit-let enc-env bindings body false))

(defmethod emit-special 'loop* [_ env [_ bindings & body]]
  (emit-let env bindings body true))

(defmethod emit-special 'case* [_ env [_ v tests thens default]]
  (let [gensym (:gensym env)
        expr? (= :expr (:context env))
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
        env (no-top-level env)]
    (str (if *repl*
           (str "globalThis."
                (when *cljs-ns*
                  (str (munge *cljs-ns*) ".") #_"var ")
                (munge name))
           (str "var " (munge name))) " = "
         (emit expr (expr-env env)) ";\n")))

(defmethod emit-special 'def [_type env [_const & more :as expr]]
  (let [name (first more)]
    ;; TODO: move *public-vars* to :ns-state atom
    (swap! *public-vars* conj (munge* name))
    (swap! (:ns-state env) (fn [state]
                             (let [current (:current state)]
                               (assoc-in state [current name] {}))))
    (let [skip-var? (:squint.compiler/skip-var (meta expr))]
      (emit-var more skip-var? env))))

(defn js-await [env more]
  (emit-return (wrap-await (emit more (expr-env env))) env))

(defmethod emit-special 'js/await [_ env [_await more]]
  (js-await env more))

(defmethod emit-special 'js-await [_ env [_await more]]
  (js-await env more))

#_(defn wrap-iife [s]
    (cond-> (format "(%sfunction () {\n %s\n})()" (if *async* "async " "") s)
      *async* (wrap-await)))

(defn resolve-ns [env alias]
  (case *target*
    :squint
    (case alias
      (squint.string clojure.string) "squint-cljs/src/squint/string.js"
      (squint.set clojure.set) "squint-cljs/src/squint/set.js"
      (if (symbol? alias)
        (if-let [resolve-ns (:resolve-ns env)]
          (or (resolve-ns alias)
              alias)
          alias)
        alias))
    :cherry
    (case alias
      (cljs.string clojure.string) "cherry-cljs/lib/clojure.string.js"
      (cljs.walk clojure.walk) "cherry-cljs/lib/clojure.walk.js"
      (cljs.set clojure.set) "cherry-cljs/lib/clojure.set.js"
      alias)
    alias))

(defn process-require-clause [env current-ns-name [libname & {:keys [refer as]}]]
  (when-not (or (= 'squint.core libname)
                (= 'cherry.core libname))
    (let [libname (resolve-ns env libname)
          [libname suffix] (str/split (if (string? libname) libname (str libname)) #"\$" 2)
          [p & _props] (when suffix
                         (str/split suffix #"\."))
          as (when as (munge as))
          expr (str
                (when (and as (= "default" p))
                  (if *repl*
                    (statement (format "const %s = (await import('%s')).default" as libname))
                    (statement (format "import %s from '%s'" as libname))))
                (when (and (not as) (not p) (not refer))
                  ;; import presumably for side effects
                  (if *repl*
                    (statement (format "await import('%s')" libname))
                    (statement (format "import '%s'" libname))))
                (when (and as (not= "default" p))
                  (swap! *imported-vars* update libname (fnil identity #{}))
                  (statement (if *repl*
                               (format "var %s = await import('%s')" as libname)
                               (format "import * as %s from '%s'" as libname))))
                (when refer
                  (swap! (:ns-state env)
                         (fn [ns-state]
                           (let [current (:current ns-state)]
                             (update-in ns-state [current :refers] (fnil into #{}) refer))))
                  (let [munged-refers (map munge refer)]
                    (if *repl*
                      (str (statement (format "var { %s } = await import('%s')" (str/join ", " munged-refers) libname))
                           (str/join (map (fn [sym]
                                            (statement (str "globalThis." (munge current-ns-name) "." sym " = " sym)))
                                          munged-refers)))
                      (statement (format "import { %s } from '%s'" (str/join ", " (map munge refer)) libname))))))]
      (when as
        (swap! (:ns-state env)
               (fn [ns-state]
                 (let [current (:current ns-state)]
                   (update-in ns-state [current :aliases] (fn [aliases]
                                                            ((fnil assoc {}) aliases as libname)))))))
      (when-not (:elide-imports env)
        expr)
      #_nil)))

(defmethod emit-special 'ns [_type env [_ns name & clauses]]
  (let [mname (munge name)
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
        #_#_ns-obj " = {aliases: {}};\n"
        (reduce-kv (fn [acc k _v]
                     (if (symbol? k)
                       (str acc
                            ns-obj "." #_".aliases." k " = " k ";\n")
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
                      (str method)
                      (comma-list (emit-args env args)))
                 env)))

(defn emit-aget [env var idxs]
  (emit-return (apply str
                      (emit var (expr-env env))
                      (interleave (repeat "[") (emit-args env idxs) (repeat "]")))
               env))

(defmethod emit-special '. [_type env [_period obj method & args]]
  (let [[method args] (if (seq? method)
                        [(first method) (rest method)]
                        [method args])
        method-str (str method)]
    (if (str/starts-with? method-str "-")
      (emit (list 'js* (str "~{}." (symbol (munge** (subs method-str 1)))) obj) env)
      (emit-method env obj (symbol method-str) args))))

(defmethod emit-special 'aget [_type env [_aget var & idxs]]
  (emit-aget env var idxs))

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
    (emit-return (str (emit target eenv) " = " (emit val eenv) statement-separator)
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

(defn wrap-parens [s]
  (str "(" s ")"))

#_#_(defmethod emit-special 'and [_type env [_ & more]]
      (if (empty? more)
        true
        (emit-return (wrap-parens (apply str (interpose " && " (emit-args env more)))) env)))

(defmethod emit-special 'or [_type env [_ & more]]
  (if (empty? more)
    nil
    (emit-return (wrap-parens (apply str (interpose " || " (emit-args env more)))) env)))

(defmethod emit-special 'while [_type env [_while test & body]]
  (str "while (" (emit test) ") { \n"
       (emit-do env body)
       "\n }"))

(defn ->sig [env sig]
  (let [gensym (:gensym env)]
    (reduce (fn [[env sig seen] param]
              (if (contains? seen param)
                (let [new-param (gensym param)
                      env (update env :var->ident assoc param (munge new-param))
                      sig (conj sig new-param)
                      seen (conj seen param)]
                  [env sig seen])
                [(update env :var->ident assoc param (munge param))
                 (conj sig param)
                 (conj seen param)]))
            [env [] #{}]
            sig)))

(defn emit-function [env _name sig body & [elide-function?]]
  ;; (assert (or (symbol? name) (nil? name)))
  (assert (vector? sig))
  (let [[env sig] (->sig env sig)]
    (binding [*recur-targets* sig]
      (let [recur? (volatile! nil)
            env (assoc env :recur-callback
                       (fn [coll]
                         (when (identical? sig coll)
                           (vreset! recur? true))))
            body (emit-do (assoc env :context :return)
                          body)
            body (if @recur?
                   (format "while(true){
%s
break;}" body)
                   body)]
        (str (when-not elide-function?
               (str (when *async*
                      "async ") "function " #_(when name
                                              (str name " "))))
             (comma-list (map munge sig))
             " {\n"
             body "\n}")))))

(defn emit-function* [env expr opts]
  (let [name (when (symbol? (first expr)) (first expr))
        expr (if name (rest expr) expr)
        expr (if (seq? (first expr))
               ;; TODO: multi-arity:
               (first expr)
               expr)]
    (-> (if name
          (let [signature (first expr)
                body (rest expr)]
            (str (when *async*
                   "async ") "function " (munge name) " "
                 (emit-function env name signature body true)))
          (let [signature (first expr)
                body (rest expr)]
            (str (emit-function env nil signature body))))
        (cond-> (and
                 (not (:squint.internal.fn/def opts))
                 (= :expr (:context env))) (wrap-parens))
        (emit-return env))))

(defmethod emit-special 'fn* [_type env [_fn & sigs :as expr]]
  (let [async? (:async (meta expr))]
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
                               env (assoc-in env [:var->ident binding] (gensym binding))]
                           (str "catch(" (emit binding (expr-env env)) "){\n"
                                (emit-do env catch-body)
                                "}\n")))
                       (when-let [[_ & finally-body] (first finally-clause)]
                         (str "finally{\n"
                              (emit-do (assoc env :context :statement) finally-body)
                              "}\n")))
            (not= :statement (:context env))
            (wrap-iife))
          (emit-return outer-env)))))

(defmethod emit-special 'funcall [_type env [fname & args :as _expr]]
  (let [ns (when (symbol? fname) (namespace fname))
        fname (if ns (symbol (munge ns) (name fname))
                  fname)
        cherry? (= :cherry *target*)
        cherry+interop? (and
                         cherry?
                         (= "js" ns))]
    (emit-return (str
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
                 env)))

(defmethod emit-special 'letfn* [_ env [_ form & body]]
  (let [bindings (take-nth 2 form)
        fns (take-nth 2 (rest form))
        sets (map (fn [binding fn]
                    `(set! ~binding ~fn))
                  bindings fns)
        let `(let ~(vec (interleave bindings (repeat nil))) ~@sets ~@body)]
    (emit let env)))

(defmethod emit-special 'zero? [_ env [_ num]]
  (bool-expr (-> (format "(%s == 0)" (emit num (assoc env :context :expr)))
                 (emit-return env))))

(defmethod emit-special 'neg? [_ env [_ num]]
  (bool-expr (-> (format "(%s < 0)" (emit num (assoc env :context :expr)))
                 (emit-return env))))

(defmethod emit-special 'pos? [_ env [_ num]]
  (bool-expr (-> (format "(%s > 0)" (emit num (assoc env :context :expr)))
                 (emit-return env))))

(defmethod emit-special 'nil? [_ env [_ obj]]
  (bool-expr (-> (format "(%s == null)" (emit obj (assoc env :context :expr)))
                 (emit-return env))))

(defmethod emit-special 'js-in [_ env [_ key obj]]
  (bool-expr (emit (list 'js* "~{} in ~{}" key obj) env)))

(defmethod emit #?(:clj clojure.lang.MapEntry :cljs MapEntry) [expr env]
  ;; RegExp case moved here:
  ;; References to the global RegExp object prevents optimization of regular expressions.
  (emit (vec expr) env))

(def special-forms '#{zero? pos? neg? js-delete nil? js-in})

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

(defmethod emit-special 'if [_type env [_if test then else :as expr]]
  ;; NOTE: I tried making the output smaller if the if is in return position
  ;; if .. return .. else return ..
  ;; => return ( .. ? : ..);
  ;; but this caused issues with recur in return position:
  ;; (defn foo [x] (if x 1 (recur (dec x)))) We might fix this another time, but
  ;; tools like eslint will rewrite in the short form anyway.
  (let [expr-env (assoc env :context :expr)
        naked-condition (emit test expr-env)
        skip-truth? (or (:bool naked-condition)
                        (:bool (meta expr))
                        (= 'boolean (:tag (meta test))))
        condition (if skip-truth?
                    naked-condition
                    (emit (list 'clojure.core/truth_ (list 'js* naked-condition)) expr-env))]
    (if (= :expr (:context env))
      (->
       (format "(%s) ? (%s) : (%s)"
               condition
               (emit then env)
               (emit else env))
       (emit-return env))
      (str (format "if (%s) {\n" condition)
           (emit then env)
           "}"
           (when (some? else)
             (str " else {\n"
                  (emit else env)
                  "}"))))))

(defn jsx-attrs [v env]
  (let [env (expr-env env)]
    (if (:jsx-runtime env)
      (when v
        (emit v (dissoc env :jsx)))
      (if (seq v)
        (str
         " "
         (str/join " "
                   (map (fn [[k v]]
                          (if (= :& k)
                            (str "{..." (emit v (dissoc env :jsx)) "}")
                            (str (name k) "=" (cond-> (emit v (assoc env :jsx false))
                                                (not (string? v))
                                                ;; since we escape here, we
                                                ;; can probably remove
                                                ;; escaping elsewhere?
                                                (escape-jsx env)))))
                        v)))
        "")
      )))
