(ns clava.eval-macro)

(defmacro evalll [expected body]
  `(cljs.test/async ~'done
                    (let [prog# (~'compile! ~body)
                          filename# (str (gensym "test") ".mjs")]
                      (fs/writeFileSync filename# prog#)
                      (-> (~'dyn-import (path/resolve (js/process.cwd) filename#))
                          (.then
                           #(do (cljs.test/is (= ~expected (.-result %)))
                                ))
                          (.finally
                           #(do (fs/unlinkSync filename#)
                                (~'done)))))))
