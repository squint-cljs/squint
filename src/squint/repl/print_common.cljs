(ns squint.repl.print-common
  "REPL value printing shared between squint and cherry (see cherry ADR 0004).
  The dialect's pr-str comes in as an argument.")

(def promise-print-timeout-ms
  "How long the REPL waits on a top-level Promise before giving up and showing
  it as `#<Promise pending>`. Kept tight so the REPL stays responsive; a slow
  Promise can be re-evaluated with an explicit `(js-await ..)`."
  1000)

(defn pr-str-repl
  "Like the dialect's `pr-str`, but if the value is a Promise, race it against
  [[promise-print-timeout-ms]] and render `#<Promise <value>>` /
  `#<Promise rejected <e>>` / `#<Promise pending>` instead of an opaque
  `#<Promise>`. Always returns a Promise<string>."
  [pr-str* v]
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
                   "rejected" (str "#<Promise rejected " (pr-str* (.-val r)) ">")
                   "resolved" (str "#<Promise " (pr-str* (.-val r)) ">")))))
    (js/Promise.resolve (pr-str* v))))
