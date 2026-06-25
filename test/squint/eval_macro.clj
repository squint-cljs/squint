(ns squint.eval-macro)

(defmacro deftest-eval
  "An ^:async deftest. `body` is a quoted squint program `(do (ns ...) forms...)`
  whose ns requires `[cljs.test :refer [is]]`. Each `(is ...)` runs in the
  compiled program, so failures report expected/actual. The macro resets the
  squint test env around the body and asserts no failures occurred."
  [name body]
  (let [[do-sym ns-form & forms] body
        program (concat
                 (list do-sym ns-form
                       (list 'cljs.test/set-env! (list 'cljs.test/empty-env)))
                 forms
                 (list (list 'def 'result
                             (list 'cljs.test/successful?
                                   (list :report-counters (list 'cljs.test/get-current-env))))))]
    `(cljs.test/deftest ~(vary-meta name assoc :async true)
       (let [prog# (~'compile! '~program)
             filename# (str (gensym "test") ".mjs")]
         (fs/writeFileSync filename# prog#)
         (try
           (let [~'mod (await (~'dyn-import (-> (path/resolve (js/process.cwd) filename#)
                                                url/pathToFileURL)))]
             (cljs.test/is (.-result ~'mod)))
           (finally
             (fs/unlinkSync filename#)))))))

(defmacro evalll [expected body]
  (let [check (if (seq? expected)
                `(~@expected (.-result ~'mod))
                `(= ~expected (.-result ~'mod)))]
    `(let [prog# (~'compile! ~body)
           filename# (str (gensym "test") ".mjs")]
       (fs/writeFileSync filename# prog#)
       (try
         (let [~'mod (await (~'dyn-import (-> (path/resolve (js/process.cwd) filename#)
                                              url/pathToFileURL)))]
           (cljs.test/is ~check))
         (finally
           (fs/unlinkSync filename#))))))
