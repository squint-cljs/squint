(ns webpack-repl-test
  "End-to-end test of the squint browser REPL in generic (webpack) mode.

  Spawns `webpack serve` (the SquintPlugin compiles cljs, starts the nREPL +
  WS servers, injects the dep manifest), loads the page with headless
  playwright, connects an nREPL client over bencode TCP, and evaluates forms.
  Exercises the whole generic-mode stack: repl-mode compile, the WS transport,
  the manifest registry (instance sharing), tier-3 on-demand esbuild bundling,
  and :hot :ws state-preserving reload."
  (:require ["node:child_process" :as cp]
            ["node:fs" :as fs]
            ["node:path" :as path]
            ["playwright$default" :as pw]
            [clojure.string :as str]
            [nrepl-client :refer [make-client nrepl-request nrepl-eval msg-field with-timeout]]))

(def PORT 5299)        ;; webpack-dev-server (isolated from 5188/5199)
(def NREPL-PORT 1341)  ;; nREPL TCP (isolated from 1339/1340)
(def URL (str "http://localhost:" PORT "/"))
(def EXDIR (path/resolve "examples/webpack-repl"))

(defn wait-output [stream substr]
  (js/Promise.
   (fn [resolve _]
     (let [acc (atom "")]
       (.on stream "data"
            (fn [d]
              (swap! acc str d)
              (when (str/includes? @acc substr) (resolve true))))))))

(defn wait-console [page substr]
  (js/Promise.
   (fn [resolve _]
     (.on page "console"
          (fn [msg] (when (str/includes? (.text msg) substr) (resolve true)))))))

(def failures (atom 0))

(defn ^:async poll-eval
  "Eval `code` every 400ms until it returns `want` or `ms` elapse; returns the
  last value seen (so the caller's check reports a mismatch)."
  [client session code want ms]
  (let [deadline (+ (js/Date.now) ms)]
    (loop []
      (let [v (await (nrepl-eval client session code))]
        (if (or (= v want) (> (js/Date.now) deadline))
          v
          (do (await (js/Promise. (fn [r _] (js/setTimeout r 400))))
              (recur)))))))

(defn check [label expected actual]
  (if (= expected actual)
    (println "PASS:" label)
    (do (swap! failures inc)
        (println "FAIL:" label "- expected" (pr-str expected) "got" (pr-str actual)))))

;; Hard safety net: never hang past 2 min (unref'd so a fast success still exits).
(.unref (js/setTimeout
         (fn [] (println "HARD TIMEOUT: test exceeded 120s") (js/process.exit 1))
         120000))

(defn ^:async run []
  (let [wp (.spawn cp "node_modules/.bin/webpack" #js ["serve"]
                   #js {:cwd EXDIR :env js/process.env})
        browser (atom nil)
        app-file (path/join EXDIR "src" "app.cljs")
        orig-app (.readFileSync fs app-file "utf8")
        restore! (fn [] (try (.writeFileSync fs app-file orig-app) (catch :default _ nil)))
        _ (.on js/process "exit" restore!)
        wlog (atom "")
        log! (fn [& xs] (swap! wlog str (apply str xs) "\n"))]
    (.on (.-stdout wp) "data" (fn [d] (swap! wlog str d)))
    (.on (.-stderr wp) "data" (fn [d] (swap! wlog str d)))
    (.on wp "error" (fn [e] (log! "[webpack spawn error] " (.-message e))))
    (.on wp "exit" (fn [code] (log! "[webpack exited] code " code)))
    (try
      (await (with-timeout 60000 "webpack ready" (wait-output (.-stdout wp) "compiled successfully")))
      (reset! browser (await (with-timeout 30000 "chromium launch"
                                           (.launch (.-chromium pw) #js {:headless true}))))
      (let [page (await (.newPage @browser))
            _ (.on page "pageerror" (fn [e] (log! "[pageerror] " (.-message e))))
            _ (.on page "console" (fn [m] (when (= "error" (.type m)) (log! "[browser console.error] " (.text m)))))
            ready (wait-console page "nrepl listener ready")]
        (await (.goto page URL))
        (await (with-timeout 30000 "browser nrepl listener ready" ready))
        (check "page rendered from bundled cljs" true
               (str/includes? (await (.textContent page "#app")) "hello from squint webpack repl"))
        (let [client (await (with-timeout 10000 "nrepl connect" (make-client NREPL-PORT)))
              clone (await (with-timeout 10000 "nrepl clone" (nrepl-request client #js {:op "clone"})))
              session (some (fn [m] (aget m "new-session")) (js/Array.from clone))
              ev (fn [code] (with-timeout 20000 (str "eval " (pr-str code))
                                          (nrepl-eval client session code)))]
          ;; 1. basic arithmetic roundtrip
          (check "eval arithmetic" "3" (await (ev "(+ 1 2)")))
          ;; a REPL-defined sentinel to prove no page reload later
          (await (ev "(ns depns) (def sentinel 42)"))
          ;; 2. require preact -> resolves via the manifest registry, so it's
          ;; the SAME instance the page bundled (compare the `h` export, stable
          ;; across namespace-object wrappers).
          (await (ev "(set! js/globalThis.__page_h (.-h js/globalThis.__page_preact))"))
          (check "preact instance shared via registry"
                 "true"
                 (await (ev (str "(ns preactns (:require [\"preact\" :as p]))"
                                 " (identical? (.-h p) js/globalThis.__page_h)"))))
          ;; 3. tier 3: lodash is installed but never required by app source, so
          ;; it's not in the manifest -> on-demand esbuild bundle over the WS.
          ;; lodash is CJS -> the $default form yields the lodash object.
          (check "lodash via tier-3 on-demand bundle"
                 "2"
                 (await (ev (str "(ns lodashns (:require [\"lodash$default\" :as ld]))"
                                 " (count (ld/chunk [1 2 3 4] 2))"))))
          ;; 4. sentinel survived -> no page reload when the new dep was bundled
          (check "new dep required without page reload (REPL state survived)"
                 "42"
                 (await (ev "(ns depns) sentinel")))
          ;; 5. a node-only spec can't be bundled for the browser; esbuild's
          ;; diagnosis surfaces as a non-empty eval error, never a hang.
          (let [resp (await (with-timeout 20000 "eval node-only dep"
                                          (nrepl-request client #js {:op "eval" :session session
                                                                     :code "(ns nodens (:require [\"node:fs\" :as nfs])) nfs"})))
                ex (msg-field resp "ex")]
            (check "node-only dep errors cleanly (non-empty message)" true
                   (boolean (and ex (pos? (count ex))))))
          ;; 6. :hot :ws - edit hot-fn in the source, wait for the recompile+load
          ;; over the WS, then eval it: the new behaviour is visible AND the
          ;; REPL sentinel still exists (state-preserving reload, no page reload).
          (check "hot-fn v1" "1" (await (ev "(app/hot-fn)")))
          (.writeFileSync fs app-file (str/replace orig-app "  1)" "  2)"))
          (check "hot-fn v2 after :hot :ws reload" "2"
                 (await (poll-eval client session "(app/hot-fn)" "2" 18000)))
          (check "REPL state survived :hot :ws reload" "42" (await (ev "(ns depns) sentinel")))))
      (catch :default e
        (swap! failures inc)
        (println "ERROR:" (.-message e))
        (println "----- webpack / browser output -----")
        (println @wlog)
        (println "------------------------------------"))
      (finally
        (restore!)
        (when @browser (try (await (.close @browser)) (catch :default _ nil)))
        (.kill wp)))
    (println (if (zero? @failures) "\nAll checks passed." (str "\n" @failures " failure(s).")))
    (js/process.exit (if (zero? @failures) 0 1))))

(run)
