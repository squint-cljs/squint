(ns squint.repl.print
  "REPL value printing helpers shared by the socket REPL and the nREPL server.

  Kept in its own ns (no `cljs.pprint` / nREPL deps) so shadow-cljs can place it
  in either consumer's module without dragging the rest of the nREPL server's
  module graph along."
  (:require
   ["squint-cljs/core.js" :as squint]))

(def promise-print-timeout-ms
  "How long the REPL waits on a top-level Promise before giving up and showing
  it as `#<Promise pending>`. Kept tight so the REPL stays responsive; a slow
  Promise can be re-evaluated with an explicit `(js-await ..)`."
  1000)

(defn pr-str-repl
  "Like squint's `pr_str`, but if the value is a Promise, race it against
  [[promise-print-timeout-ms]] and render `#<Promise <value>>` /
  `#<Promise rejected <e>>` / `#<Promise pending>` instead of an opaque
  `#<Promise>`. Always returns a Promise<string>."
  [v]
  (if (instance? js/Promise v)
    (-> (js/Promise.race
         #js [(-> v
                  (.then (fn [r] #js {:tag "resolved" :val r}))
                  (.catch (fn [e] #js {:tag "rejected" :val e})))
              (js/Promise. (fn [resolve _]
                             (js/setTimeout
                              #(resolve #js {:tag "pending"})
                              promise-print-timeout-ms)))])
        (.then (fn [^js r]
                 (case (.-tag r)
                   "pending" "#<Promise pending>"
                   "rejected" (str "#<Promise rejected " (squint/pr_str (.-val r)) ">")
                   "resolved" (str "#<Promise " (squint/pr_str (.-val r)) ">")))))
    (js/Promise.resolve (squint/pr_str v))))
