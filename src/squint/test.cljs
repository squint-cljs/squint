(ns squint.test
  (:require [clojure.string]))

(def ^:dynamic *current-env* nil)

(defn empty-env []
  {:report-counters {:test 0 :pass 0 :fail 0 :error 0}
   :testing-vars ()
   :testing-contexts ()
   :once-fixtures {}
   :each-fixtures {}})

(defn get-current-env []
  (or *current-env* (empty-env)))

(defn set-env! [env]
  (set! *current-env* env)
  env)

(defn clear-env! []
  (set! *current-env* nil)
  nil)

(defn update-current-env! [ks f & args]
  (let [env (get-current-env)
        new-env (apply update-in env ks f args)]
    (set-env! new-env)))

(defn testing-contexts-str []
  (when-let [contexts (seq (:testing-contexts (get-current-env)))]
    (clojure.string/join " " (reverse contexts))))

(defn testing-vars-str []
  (when-let [vars (seq (:testing-vars (get-current-env)))]
    (clojure.string/join " " (map str vars))))

(defn inc-report-counter! [name]
  (when (:report-counters (get-current-env))
    (update-current-env! [:report-counters name] (fnil inc 0))))

(defn current-test-str []
  (let [vars (testing-vars-str)
        ctx (testing-contexts-str)]
    (cond
      (and vars ctx) (str vars " " ctx)
      vars vars
      ctx ctx
      :else "test")))

(defn report [{:keys [type message expected actual line column file] :as m}]
  (when (contains? #{:pass :fail :error} type)
    (inc-report-counter! type))
  (let [location (when (or line column file)
                   (str (when file (str file ":"))
                        (when line line)
                        (when column (str ":" column))))]
    (case type
      :pass nil
      :fail (do
              (js/console.error (str "FAIL in " (current-test-str)
                                     (when location (str " (" location ")"))))
              (when message (js/console.error "  " message))
              (js/console.error "  expected:" (pr-str expected))
              (js/console.error "    actual:" (pr-str actual)))
      :error (do
               (js/console.error (str "ERROR in " (current-test-str)
                                      (when location (str " (" location ")"))))
               (when message (js/console.error "  " message))
               (when expected (js/console.error "  expected:" (pr-str expected)))
               (js/console.error "    actual:" (pr-str actual)))
      :begin-test-ns (js/console.log "\nTesting" (str (:ns m)))
      :end-test-ns nil
      :begin-test-var nil
      :end-test-var nil
      :summary (let [{:keys [test pass fail error]} (:report-counters (get-current-env))]
                 (js/console.log "\nRan" test "tests containing" (+ pass fail error) "assertions.")
                 (js/console.log (str fail) "failures," (str error) "errors."))
      (js/console.log "Unknown report type:" type m))))

(defn successful? [results]
  (and (zero? (:fail results 0))
       (zero? (:error results 0))))

(defn async? [x]
  (instance? js/Promise x))

(defn wrap-async
  "Wraps setup/teardown fns into async-aware fixture.
  Teardown waits for Promise resolution if test is async."
  [setup teardown]
  (fn [test-fn]
    (setup)
    (let [result (test-fn)]
      (if (async? result)
        (.finally result teardown)
        (do (teardown) result)))))

(defn compose-fixtures [f1 f2]
  (fn [g] (f1 (fn [] (f2 g)))))

(defn join-fixtures [fixtures]
  (reduce compose-fixtures (fn [f] (f)) fixtures))

(defn get-each-fixtures
  "Returns the each-fixture vector for ns-str, or [] if none. The 1-arg
  variant defaults to the nil-keyed bucket, used for direct calls that
  predate per-ns fixtures."
  ([] (get-each-fixtures nil))
  ([ns-str] (get-in (get-current-env) [:each-fixtures ns-str] [])))

(defn set-each-fixtures!
  ([fixtures] (set-each-fixtures! nil fixtures))
  ([ns-str fixtures]
   (update-current-env! [:each-fixtures ns-str] (constantly fixtures))))

(defn get-once-fixtures
  ([] (get-once-fixtures nil))
  ([ns-str] (get-in (get-current-env) [:once-fixtures ns-str] [])))

(defn set-once-fixtures!
  ([fixtures] (set-once-fixtures! nil fixtures))
  ([ns-str fixtures]
   (update-current-env! [:once-fixtures ns-str] (constantly fixtures))))

(defn test-var [v]
  (when (fn? v)
    (let [test-name (or (:name (meta v)) "anonymous")
          ns-str (:ns (meta v))
          ;; Per-ns each-fixtures, falling back to the nil-keyed bucket
          ;; for fixtures set via the 1-arg legacy form.
          each-fixtures (let [per-ns (get-each-fixtures ns-str)]
                          (if (seq per-ns) per-ns (get-each-fixtures)))
          wrapped-test (if (seq each-fixtures)
                         (fn [] ((join-fixtures each-fixtures) v))
                         v)
          pop-test-name! #(update-current-env! [:testing-vars] rest)]
      (update-current-env! [:testing-vars] conj test-name)
      (inc-report-counter! :test)
      (try
        (let [result (wrapped-test)]
          (if (async? result)
            (-> result
                (.then (fn [r] (pop-test-name!) r))
                (.catch (fn [e]
                          (report {:type :error :message (.-message e) :expected nil :actual e})
                          (pop-test-name!))))
            (do (pop-test-name!) result)))
        (catch :default e
          (pop-test-name!)
          (report {:type :error :message (.-message e) :expected nil :actual e}))))))

(def ^:private test-registry (atom {}))

(defn register-test!
  "Registers a deftest fn under its namespace for later discovery by run-tests.
  Idempotent: re-registration replaces the previous fn with the same name."
  [ns-str test-fn]
  (let [test-name (:name (meta test-fn))]
    (swap! test-registry assoc-in [ns-str test-name] test-fn))
  test-fn)

(defn registered-tests
  "Returns a vector of test fns registered for the given namespace name.
  With no argument, returns all registered tests across every namespace."
  ([] (vec (mapcat vals (vals @test-registry))))
  ([ns-str] (vec (vals (get @test-registry ns-str)))))

(defn- run-vars-with-once-fixtures [ns-str vars]
  (let [per-ns (get-once-fixtures ns-str)
        once-fixtures (if (seq per-ns) per-ns (get-once-fixtures))
        run-all (fn []
                  (reduce
                   (fn [chain v]
                     (if (async? chain)
                       (.then chain (fn [_] (test-var v)))
                       (test-var v)))
                   nil
                   vars))
        run-with-fixtures (fn []
                            (if (seq once-fixtures)
                              ((join-fixtures once-fixtures) run-all)
                              (run-all)))]
    ;; Match cljs.test: bracket each ns's run with :begin/:end-test-ns
    ;; events so reporters can group output. Anonymous (nil-ns) groups
    ;; — explicit fns without :ns meta — are skipped so we don't print
    ;; "Testing " with no name.
    (when ns-str (report {:type :begin-test-ns :ns ns-str}))
    (let [chain (run-with-fixtures)
          finish (fn [r]
                   (when ns-str (report {:type :end-test-ns :ns ns-str}))
                   r)]
      (if (async? chain)
        (.then chain finish)
        (finish chain)))))

(def ^:private fresh-counters {:test 0 :pass 0 :fail 0 :error 0})

(defn run-tests
  "Runs tests and reports a summary. Accepts any of:
   - no args: run every registered test across all namespaces.
   - namespace name strings: run tests registered under each given ns.
   - explicit test fns: run those fns directly.
   Initializes the env if none is set. Tests are grouped by their ns
   (from deftest's metadata) so each ns's once-fixtures wrap that ns's
   tests, matching cljs.test.

   Counters are scoped to this call: the caller's :report-counters are
   saved on entry and restored before return, so an inner run-tests
   doesn't pollute an outer run's totals (matches clojure.test, where
   *report-counters* is rebound per test-ns).

   Returns (or resolves to, for async tests) the :report-counters
   summary map for this run."
  [& args]
  (let [test-vars (cond
                    (empty? args)
                    (registered-tests)
                    (string? (first args))
                    (vec (mapcat registered-tests args))
                    :else
                    args)
        _ (when (nil? *current-env*) (set-env! (empty-env)))
        saved-counters (:report-counters (get-current-env))
        _ (update-current-env! [:report-counters] (constantly fresh-counters))
        ;; preserve insertion order while grouping by ns
        groups (reduce (fn [acc v]
                         (let [k (:ns (meta v))]
                           (update acc k (fnil conj []) v)))
                       {} test-vars)
        run-groups (fn []
                     (reduce (fn [chain [ns-str vars]]
                               (if (async? chain)
                                 (.then chain (fn [_] (run-vars-with-once-fixtures ns-str vars)))
                                 (run-vars-with-once-fixtures ns-str vars)))
                             nil groups))
        finish (fn [_]
                 (report {:type :summary})
                 (let [counters (:report-counters (get-current-env))]
                   (update-current-env! [:report-counters] (constantly saved-counters))
                   counters))
        chain (run-groups)]
    (if (async? chain)
      (.then chain finish)
      (finish nil))))
