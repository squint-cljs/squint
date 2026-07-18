(ns squint.internal.cli
  (:require
   [shadow.esm :as esm]
   [squint.compiler :as cc]
   [squint.compiler.node :as compiler]
   [squint.internal.cli-common :as cli-common]
   [squint.repl.node :as repl]
   #_[squint.repl.nrepl-server :as nrepl]))

(defn start-nrepl [{:keys [opts]}]
  (-> (esm/dynamic-import "./node.nrepl_server.js")
      (.then (fn [^js val]
               ((.-startServer val) opts)))))

(def nrepl-server-spec
  {:host {:desc "Host on which to expose server (0.0.0.0 to allow network access)"
          :ref "<host>"
          :default "127.0.0.1"
          :coerce :string}
   :port {:desc "Port on which to expose server (0 to pick a random port)"
          :ref "<port>"
          :default 0
          :coerce :long}})
(def nrepl-server-opt-order [:host :port :help])

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
   {:cmds ["nrepl-server"]
    :doc "Start an nREPL server."
    :spec nrepl-server-spec
    :order nrepl-server-opt-order
    :fn (fn [m] (cli-common/args-validate (assoc m :arg-count 0)) (start-nrepl m))}
   (cli-common/eval-cmd dialect)])

(defn init []
  (cli-common/init dialect table))
