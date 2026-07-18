(ns squint.repl.nrepl-server
  "Squint's nREPL server: squint.repl.nrepl-server-common with squint's
  compiler. The shadow :node.nrepl_server module exports startServer,
  handleBrowserMessage and evalString from here (vite.js imports them)."
  (:require
   [squint.compiler :as compiler]
   [squint.compiler.node :as compiler-node]
   [squint.repl.nrepl-server-common :as common]
   [squint.repl.print :as rp]))

(common/set-dialect!
 {:compile-string* compiler/compile-string*
  :resolve-ns-repl compiler-node/resolve-ns-repl
  :config-file "squint.edn"
  :pr-str-repl rp/pr-str-repl})

(def start-server common/start-server)
(def handle-browser-message common/handle-browser-message)
(def evaluate-string common/evaluate-string)
