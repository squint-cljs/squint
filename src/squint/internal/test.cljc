(ns squint.internal.test)

(defn assert-expr [msg form]
  (let [op (when (sequential? form) (first form))
        loc (meta form)
        line (:line loc)
        column (:column loc)
        report (fn [type expected actual ret]
                 `(do (clojure.test/report {:type ~type :message ~msg :expected ~expected :actual ~actual
                                            ~@(when line [:line line])
                                            ~@(when column [:column column])})
                      ~ret))
        default (let [sym (gensym "value")]
                  `(let [~sym ~form]
                     (if ~sym
                       ~(report :pass (pr-str form) sym sym)
                       ~(report :fail (pr-str form) sym sym))))]
    (case op
      = (if (= 2 (count (rest form)))
          (let [[expected actual] (rest form)
                expected-sym (gensym "expected")
                actual-sym (gensym "actual")
                result-sym (gensym "result")]
            `(let [~expected-sym ~expected
                   ~actual-sym ~actual
                   ~result-sym (= ~expected-sym ~actual-sym)]
               (if ~result-sym
                 ~(report :pass expected-sym actual-sym true)
                 ~(report :fail expected-sym actual-sym false))))
          default)
      thrown? (let [klass (second form)
                    body (nthnext form 2)
                    e-sym (gensym "e")]
                `(try
                   (do ~@body)
                   ~(report :fail (pr-str form) "No exception thrown" false)
                   (catch :default ~e-sym
                     (if (instance? ~klass ~e-sym)
                       ~(report :pass (pr-str form) e-sym true)
                       ~(report :fail (pr-str form) e-sym false)))))
      thrown-with-msg? (let [klass (second form)
                             re (nth form 2)
                             body (nthnext form 3)
                             e-sym (gensym "e")]
                         `(try
                            (do ~@body)
                            ~(report :fail (pr-str form) "No exception thrown" false)
                            (catch :default ~e-sym
                              (if (instance? ~klass ~e-sym)
                                (if (re-find ~re (.-message ~e-sym))
                                  ~(report :pass (pr-str form) e-sym true)
                                  ~(report :fail (pr-str form) `(str "Exception message \"" (.-message ~e-sym) "\" did not match " ~re) false))
                                ~(report :fail (pr-str form) e-sym false)))))
      default)))

(defn core-deftest [_&form &env name & body]
  (let [fn-meta (select-keys (meta name) [:async])
        fsym (gensym "fn")
        ns-name (some-> &env :ns :name str)]
    `(def ~(vary-meta name assoc :test true)
       (let [~fsym ~(with-meta `(fn [] ~@body) fn-meta)]
         (set! (.-_squintTestName ~fsym) ~(str name))
         ~@(when ns-name
             [`(clojure.test/register-test! ~ns-name ~fsym)])
         ~fsym))))

(defn core-is
  ([&form &env form] (core-is &form &env form nil))
  ([&form _&env form msg]
   (let [loc (meta &form)
         form-with-meta (if (and loc (or (sequential? form) (symbol? form)))
                          (with-meta form loc)
                          form)]
     (assert-expr msg form-with-meta))))

(defn core-testing [_&form _&env string & body]
  `(do
     (clojure.test/update-current-env! [:testing-contexts] conj ~string)
     (try
       ~@body
       (finally
         (clojure.test/update-current-env! [:testing-contexts] rest)))))

(defn core-deftest- [&form &env name & body]
  (apply core-deftest &form &env (vary-meta name assoc :private true) body))

(defn core-are [_&form _&env bindings expr & args]
  (assert (pos? (count bindings)) "are requires at least one binding")
  (assert (seq args) "are requires at least one test case")
  (let [binding-count (count bindings)]
    (assert (zero? (mod (count args) binding-count))
            (str "are: arg count (" (count args) ") must be divisible by binding count (" binding-count ")"))
    `(do ~@(for [arg-group (partition binding-count args)]
             `(clojure.test/is (let [~@(interleave bindings arg-group)] ~expr))))))

(defn core-use-fixtures [_&form _&env type & fns]
  (case type
    :once `(clojure.test/set-once-fixtures! [~@fns])
    :each `(clojure.test/set-each-fixtures! [~@fns])))

(defn core-run-tests [_&form &env & args]
  ;; Match cljs.test: with no args, default to the compile-time current ns.
  ;; The :squint.compiler/skip-macro meta stops the emission from landing back
  ;; in this macro's lookup (which shares the name with the runtime fn).
  (let [ns-name (some-> &env :ns :name str)
        args' (if (and ns-name (empty? args))
                [ns-name]
                args)]
    (with-meta `(clojure.test/run-tests ~@args')
               {:squint.compiler/skip-macro true})))

(def core-test-macros
  {'deftest core-deftest
   'deftest- core-deftest-
   'is core-is
   'testing core-testing
   'are core-are
   'use-fixtures core-use-fixtures
   'run-tests core-run-tests})
