(ns squint.compiler.sci
  (:require
   ["node:fs" :as fs]
   ["node:module" :refer [createRequire]]
   ["node:path" :as path]
   ["node:url" :refer [pathToFileURL]]
   [sci.core :as sci]
   [sci.ctx-store :as ctx-store]
   [squint.compiler.node :as cn :refer [sci]]
   [squint.internal.node.utils :refer [resolve-file]]
   [squint.internal.test :as internal-test]))

(defn slurp [f]
  (fs/readFileSync f "utf-8"))

(def cwd (.-cwd js/process))

(def require-from-cwd
  (createRequire
   (-> (path/join (cwd) "noop.js")
       pathToFileURL
       .-href)))

(defn analyzer-resolve
  "Compat shim for cljs.analyzer.api/resolve. Macros use it to probe whether
  a var exists at compile time. Returns a minimal {:name sym} map when sym
  names a known squint.core var, a var defined in the current namespace, a
  built-in macro or a user macro, else nil. Macros get :macro true. A name
  that is both a core var and a built-in (inline) macro counts as a var,
  like in CLJS. A name in :refer-clojure :exclude resolves to core neither
  as var nor as macro. A local binding shadows all of these and gets
  :local true, where CLJS reports the binding kind (:let, :arg). :name is
  qualified like in CLJS: cljs.core for core, the current namespace for
  user vars, bare for locals."
  [env sym]
  (let [nm (name sym)
        nm-sym (symbol nm)
        munged (str (munge nm))
        ns-state (some-> (:ns-state env) deref)
        current (:current ns-state)
        user-vars (get-in ns-state [current :vars])
        excluded? (contains? (get-in ns-state [current :excludes]) nm-sym)
        user-macro-ns (if-let [ns* (namespace sym)]
                        (let [ns-sym (symbol ns*)]
                          (or (when (get-in ns-state [:macros ns-sym nm-sym]) ns-sym)
                              (let [aliased (get-in ns-state [current :aliases ns-sym])]
                                (when (and (symbol? aliased)
                                           (get-in ns-state [:macros aliased nm-sym]))
                                  aliased))))
                        (when-let [refer-ns (get-in ns-state [current :refers nm-sym])]
                          (when (get-in ns-state [:macros refer-ns nm-sym])
                            refer-ns)))]
    (cond
      (and (nil? (namespace sym))
           (contains? (:var->ident env) nm-sym))
      {:name nm-sym :local true}

      (or (contains? user-vars munged)
          (contains? user-vars (symbol munged)))
      {:name (symbol (str (or current "user")) nm)}

      (and (not excluded?)
           (contains? (:core-vars env) (symbol munged)))
      {:name (symbol "cljs.core" nm)}

      (and (not excluded?)
           (contains? (:core-macros env) nm-sym))
      {:name (symbol "cljs.core" nm) :macro true}

      user-macro-ns
      {:name (symbol (str user-macro-ns) nm) :macro true})))

(def analyzer-api-ns
  {'resolve analyzer-resolve})

;; Expose the compiler's assert-expr multimethod so a compile-time user defmethod extends the object the `is` built-in dispatches on.
(def test-ns
  {'assert-expr internal-test/assert-expr})

(declare ctx)
(def ctx (sci/init {:load-fn (fn [{:keys [namespace]}]
                               (if (string? namespace)
                                 (let [mod (require-from-cwd namespace)]
                                   (sci/add-js-lib! ctx namespace mod)
                                   ;; empty map = SCI will take care of aliases, refer, etc.
                                   {})
                                 (when-let [f (resolve-file namespace)]
                                   (let [fstr (slurp f)]
                                     ;; flagged ns -> its compile-time part only
                                     {:source (or (cn/compile-time-source fstr) fstr)}))))
                    :namespaces {'squint.analyzer.api analyzer-api-ns
                                 'cljs.analyzer.api analyzer-api-ns
                                 'cljs.test test-ns
                                 'clojure.test test-ns
                                 'squint.test test-ns}
                    :classes {:allow :all
                              'js js/globalThis}
                    :features #{:squint :cljs}}))

(ctx-store/reset-ctx! ctx)

(sci/alter-var-root sci/print-fn (constantly *print-fn*))
(sci/alter-var-root sci/print-err-fn (constantly *print-err-fn*))

(sci/enable-unrestricted-access!)

(defn init []
  (reset! sci {:resolve-file resolve-file
               :eval-form (fn [form _cfg]
                            (sci/eval-form ctx form))}))
