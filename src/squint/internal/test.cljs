(ns squint.internal.test)

(defn deftest
  [_ _ name & body]
  `(do (defn ~name []
         (cljs.test/test-var ~name))
       (set! (.-test ~name) (fn [] ~@body))))

(defn assert-any
  "Returns generic assertion code for any test, including macros, Java
  method calls, or isolated symbols."
  {:added "1.1"}
  [msg form]
  `(let [value# ~form]
     (if value#
       (cljs.test/do-report {:type :pass, :message ~msg,
                             :expected '~form, :actual value#})
       (cljs.test/do-report {:type :fail, :message ~msg,
                             :file clojure.core/*file*
                             :line ~(:line (meta form))
                             :expected '~form, :actual value#}))
     value#))

(defn ^:macro try-expr
  "Used by the 'is' macro to catch unexpected exceptions.
  You don't call this."
  [_ _&env msg form]
  (let [{:keys [file line end-line column end-column]} (meta form)]
    `(try
       ~(assert-any msg form)
       (catch :default t#
         (cljs.test/report
          {:type :error, :message ~msg,
           :file ~file :line ~line :end-line ~end-line :column ~column :end-column ~end-column
           :expected '~form, :actual t#})))))

(defn is
  ([_ _ form]
   `(cljs.test/is ~form nil))
  ([_ _ form msg]
   `(cljs.test/try-expr ~msg ~form)))
