(ns squint.eval-macro)

(defmacro evalll [expected body]
  `(cljs.test/async ~'done
                    (let [prog# (~'compile! ~body)
                          filename# (str (gensym "test") ".mjs")]
                      (fs/writeFileSync filename# prog#)
                      ;; (println :prog)
                      ;; (println prog#)
                      (-> (~'dyn-import (-> (path/resolve (js/process.cwd) filename#)
                                            url/pathToFileURL))
                          (.then
                           (fn [~'mod]
                             (do (cljs.test/is
                                  ~(if (not (seq? expected))
                                     `(= ~expected (.-result ~'mod))
                                     `(~@expected (.-result ~'mod)))))))
                          (.finally
                           #(do (fs/unlinkSync filename#)
                                (~'done)))))))
