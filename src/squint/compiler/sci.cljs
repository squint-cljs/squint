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
  names a known squint.core var or a var defined in the current namespace,
  else nil."
  [env sym]
  (let [nm (name sym)
        munged (str (munge nm))
        ns-state (some-> (:ns-state env) deref)
        current (:current ns-state)
        user-vars (get-in ns-state [current :vars])]
    (when (or (contains? (:core-vars env) (symbol munged))
              (contains? user-vars munged)
              (contains? user-vars (symbol munged)))
      {:name (symbol nm)})))

(def analyzer-api-ns
  {'resolve analyzer-resolve})

;; Expose the compiler's assert-expr multimethod so compile-time user code
;; (a (defmethod cljs.test/assert-expr 'op ...) in a loaded macro ns) extends
;; the same MultiFn the `is` built-in dispatches on. One instance under every
;; alias the compiler honors for the test namespace.
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
                                     {:source fstr}))))
                    :namespaces {'squint.analyzer.api analyzer-api-ns
                                 'cljs.analyzer.api analyzer-api-ns
                                 'cljs.test test-ns
                                 'clojure.test test-ns
                                 'squint.test test-ns}
                    :classes {:allow :all
                              'js js/globalThis}
                    ;; :squint/compile-time is the compile-only reader feature
                    ;; (analogous to cljs's :clj): present here in the macro-
                    ;; expansion reader, absent from the target reader, so
                    ;; #?(:squint/compile-time ...) code runs at compile time but
                    ;; is never emitted to JS.
                    :features #{:squint :cljs :squint/compile-time}}))

(ctx-store/reset-ctx! ctx)

(sci/alter-var-root sci/print-fn (constantly *print-fn*))
(sci/alter-var-root sci/print-err-fn (constantly *print-err-fn*))

(sci/enable-unrestricted-access!)

(defn init []
  (reset! sci {:resolve-file resolve-file
               :eval-form (fn [form _cfg]
                            (sci/eval-form ctx form))}))
