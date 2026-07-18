(ns squint.repl.print
  "REPL value printing helpers shared by the socket REPL and the nREPL server.

  Kept in its own ns (no `cljs.pprint` / nREPL deps) so shadow-cljs can place it
  in either consumer's module without dragging the rest of the nREPL server's
  module graph along."
  (:require
   ["squint-cljs/core.js" :as squint]
   [squint.repl.print-common :as common]))

(def promise-print-timeout-ms common/promise-print-timeout-ms)

(defn pr-str-repl
  "See squint.repl.print-common/pr-str-repl, with squint's pr_str."
  [v]
  (common/pr-str-repl squint/pr_str v))
