(ns nrepl-node-test
  "End-to-end test of the squint nREPL server on the node path (local eval, no
  browser transport - i.e. how `squint nrepl-server` runs). Starts the server,
  connects over bencode TCP, and exercises info/eldoc/complete - including js/
  interop completion against node's own globals. The browser path is covered by
  browser_repl_test."
  (:require ["squint-cljs/lib/node.nrepl_server.js" :refer [startServer]]
            [nrepl-client :refer [make-client nrepl-request msg-field with-timeout]]))

(def NREPL-PORT 17890)

(def failures (atom 0))

(defn check [label expected actual]
  (if (= expected actual)
    (println "PASS:" label)
    (do (swap! failures inc)
        (println "FAIL:" label "- expected" (pr-str expected) "got" (pr-str actual)))))

(defn candidates
  "Candidate strings from a `complete` response (vector of \"candidate\" values)."
  [msgs]
  (let [cs (msg-field msgs "completions")]
    (if cs (map (fn [c] (aget c "candidate")) (js/Array.from cs)) [])))

(defn ^:async req [client op]
  (await (with-timeout 10000 (str "nrepl op " (aget op "op")) (nrepl-request client op))))

;; never hang past 60s
(.unref (js/setTimeout
         (fn [] (println "HARD TIMEOUT: test exceeded 60s") (js/process.exit 1))
         60000))

(defn ^:async run []
  (await (startServer #js {:port NREPL-PORT}))
  (let [client (await (with-timeout 10000 "nrepl connect" (make-client NREPL-PORT)))]
    ;; define a fn + a plain var in a session ns; ns-state captures arglists/doc
    (await (req client #js {:op "eval" :session "s" :code "(ns nodetest)"}))
    (await (req client #js {:op "eval" :session "s"
                            :code "(defn greet \"say hi\" [name greeting] (str greeting name))"}))
    (await (req client #js {:op "eval" :session "s" :code "(def answer 42)"}))

    (let [info (await (req client #js {:op "info" :sym "greet" :ns "nodetest"}))]
      (check "info arglists-str" "[name greeting]" (msg-field info "arglists-str"))
      (check "info doc" "say hi" (msg-field info "doc")))

    (let [eldoc (await (req client #js {:op "eldoc" :sym "greet" :ns "nodetest"}))]
      (check "eldoc docstring" "say hi" (msg-field eldoc "docstring")))

    (let [unknown (await (req client #js {:op "info" :sym "nope-nope" :ns "nodetest"}))
          st (msg-field unknown "status")]
      (check "info unknown -> no-info" true (boolean (and st (.includes st "no-info")))))

    (let [cpl (await (req client #js {:op "complete" :prefix "gr" :ns "nodetest"}))]
      (check "complete finds greet" true (boolean (some (fn [c] (= "greet" c)) (candidates cpl)))))

    ;; js/ interop completion against node's globals (no browser transport)
    (let [js-cpl (await (req client #js {:op "complete" :prefix "js/Ma" :ns "nodetest"}))]
      (check "js complete finds js/Math" true
             (boolean (some (fn [c] (= "js/Math" c)) (candidates js-cpl)))))
    (let [js-mem (await (req client #js {:op "complete" :prefix "js/console.lo" :ns "nodetest"}))]
      (check "js complete finds js/console.log" true
             (boolean (some (fn [c] (= "js/console.log" c)) (candidates js-mem)))))

    (println (if (zero? @failures) "\nAll checks passed." (str "\n" @failures " failure(s).")))
    (js/process.exit (if (zero? @failures) 0 1))))

(run)
