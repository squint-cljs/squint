(ns squint.test
  (:require [clojure.string]))

(def ^:dynamic *current-env* nil)

(defn empty-env []
  {:report-counters {:test 0 :pass 0 :fail 0 :error 0}
   :testing-vars ()
   :testing-contexts ()
   :once-fixtures []
   :each-fixtures []})

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
  (inc-report-counter! type)
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

(defn get-each-fixtures []
  (get-in (get-current-env) [:each-fixtures] []))

(defn set-each-fixtures! [fixtures]
  (update-current-env! [:each-fixtures] (constantly fixtures)))

(defn get-once-fixtures []
  (get-in (get-current-env) [:once-fixtures] []))

(defn set-once-fixtures! [fixtures]
  (update-current-env! [:once-fixtures] (constantly fixtures)))

(defn test-var [v]
  (when (fn? v)
    (let [test-name (or (.-_squintTestName v) (:name (meta v)) "anonymous")
          each-fixtures (get-each-fixtures)
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

(defn run-tests
  "Runs test-vars with once-fixtures. Returns Promise if any test is async."
  [& test-vars]
  (let [once-fixtures (get-once-fixtures)
        run-all (fn []
                  (reduce
                   (fn [chain v]
                     (if (async? chain)
                       (.then chain (fn [_] (test-var v)))
                       (test-var v)))
                   nil
                   test-vars))]
    (if (seq once-fixtures)
      ((join-fixtures once-fixtures) run-all)
      (run-all))))
