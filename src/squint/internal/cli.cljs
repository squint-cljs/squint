(ns squint.internal.cli
  (:require
   [squint.compiler :as cc]
   [squint.compiler.node :as compiler]
   [squint.internal.cli-common :as cli-common]
   [squint.repl.node :as repl]))

(def dialect
  {:prog "squint"
   :log-prefix "[squint]"
   :config-file "squint.edn"
   :compile-file compiler/compile-file
   :compile-string cc/compile-string
   ;; same resolution compile-file uses for callers that don't supply :resolve-ns
   :resolve-ns compiler/resolve-ns
   :eval-tmp-file "squint.mjs"})

(def table
  [(cli-common/run-cmd dialect)
   (cli-common/compile-cmd dialect)
   (cli-common/watch-cmd dialect)
   {:cmds ["repl"]
    :doc "Start a REPL."
    :fn (fn [m] (cli-common/args-validate (assoc m :arg-count 0)) (repl/repl m))}
   {:cmds ["socket-repl"]
    :doc "Start a socket REPL."
    :fn (fn [m] (cli-common/args-validate (assoc m :arg-count 0)) (repl/socket-repl m))}
   (cli-common/nrepl-server-cmd dialect)
   (cli-common/eval-cmd dialect)])

(defn init []
  (cli-common/init dialect table))
