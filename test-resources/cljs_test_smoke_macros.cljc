(ns cljs-test-smoke-macros)

#?(:squint
   ^:squint/compile-time
   (defmethod cljs.test/assert-expr 'smoke/throws?
     [_menv msg form]
     (let [body (rest form)]
       `(try
          (do ~@body)
          (cljs.test/report {:type :fail :message ~msg :expected '~form :actual "no exception thrown"})
          (catch :default e#
            (cljs.test/report {:type :pass :message ~msg :expected '~form :actual e#})
            e#)))))
