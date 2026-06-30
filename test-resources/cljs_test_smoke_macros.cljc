(ns cljs-test-smoke-macros)

;; Regression coverage for user-extensible assert-expr: a compile-time
;; defmethod for a custom op, registered the same way external test suites
;; register their portable assertions (e.g. p/thrown?). The :squint/compile-time
;; reader feature is present only in the compiler's macro-expansion environment,
;; so this runs at compile time and is never emitted to JS output.
#?(:squint/compile-time
   (defmethod cljs.test/assert-expr 'smoke/throws?
     [_menv msg form]
     (let [body (rest form)]
       `(try
          (do ~@body)
          (cljs.test/report {:type :fail :message ~msg :expected '~form :actual "no exception thrown"})
          (catch :default e#
            (cljs.test/report {:type :pass :message ~msg :expected '~form :actual e#})
            e#)))))
