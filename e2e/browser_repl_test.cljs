(ns browser-repl-test
  "End-to-end test of the squint browser REPL, written in squint.

  Spawns `vite dev` (which starts the nREPL server + serves the app), loads the
  page with a headless browser via playwright, connects an nREPL client over
  bencode TCP, and evaluates forms. Exercises the whole stack: compiler REPL
  output, /@resolve-deps ns fallback, nREPL bencode, vite-WS browser eval, and
  cross-ns redef visibility (the live-global alias binding)."
  (:require ["node:net" :as net]
            ["node:child_process" :as cp]
            ["node:fs" :as fs]
            ["node:path" :as path]
            ["playwright$default" :as pw]
            [clojure.string :as str]))

(def PORT 5199)        ;; vite dev (isolated from the :5188 dev server)
(def NREPL-PORT 1340)  ;; nREPL TCP (isolated from :1339)
(def URL (str "http://localhost:" PORT "/"))
(def EXDIR (path/resolve "examples/browser-repl"))

;; ---------------------------------------------------------------- bencode ----

(defn- bbuf [s] (js/Buffer.from s "utf8"))

(defn bencode [x]
  (cond
    (number? x) (bbuf (str "i" x "e"))
    (string? x) (let [b (js/Buffer.from x "utf8")]
                  (js/Buffer.concat #js [(bbuf (str (.-length b) ":")) b]))
    (array? x) (js/Buffer.concat (js/Array.from
                                  (concat [(bbuf "l")] (map bencode x) [(bbuf "e")])))
    :else ;; plain JS object -> dict with sorted keys
    (let [ks (.sort (js/Object.keys x))
          parts (mapcat (fn [k] [(bencode k) (bencode (aget x k))]) ks)]
      (js/Buffer.concat (js/Array.from (concat [(bbuf "d")] parts [(bbuf "e")]))))))

(declare bdecode)

(defn- bdecode-coll [b i close-fn]
  (loop [j (inc i) items #js []]
    (cond
      (>= j (.-length b)) nil
      (= (aget b j) 0x65) (close-fn items (inc j))
      :else (when-let [r (bdecode b j)]
              (.push items (aget r 0))
              (recur (aget r 1) items)))))

(defn bdecode
  "Decode one value at index `i` of buffer `b`. Returns #js [value end] or nil
  when the buffer doesn't yet hold a complete value."
  [b i]
  (when (< i (.-length b))
    (let [c (aget b i)]
      (cond
        (= c 0x69) ;; i<int>e
        (let [e (.indexOf b 0x65 i)]
          (when (>= e 0)
            #js [(js/parseInt (.toString b "ascii" (inc i) e) 10) (inc e)]))
        (= c 0x6c) ;; l..e
        (bdecode-coll b i (fn [items end] #js [items end]))
        (= c 0x64) ;; d..e
        (bdecode-coll b i (fn [items end]
                            (let [o #js {}]
                              (loop [k 0]
                                (when (< k (.-length items))
                                  (aset o (aget items k) (aget items (inc k)))
                                  (recur (+ k 2))))
                              #js [o end])))
        :else ;; <len>:<bytes>
        (let [colon (.indexOf b 0x3a i)]
          (when (>= colon 0)
            (let [len (js/parseInt (.toString b "ascii" i colon) 10)
                  s (inc colon)
                  e (+ s len)]
              (when (<= e (.-length b))
                #js [(.toString b "utf8" s e) e]))))))))

;; ------------------------------------------------------------ nREPL client ----

(defn make-client [port]
  (js/Promise.
   (fn [resolve reject]
     (let [sock (.connect net port "127.0.0.1")
           buf (atom (js/Buffer.alloc 0))
           handlers (atom [])]
       (.on sock "error" reject)
       (.on sock "data"
            (fn [data]
              (reset! buf (js/Buffer.concat #js [@buf data]))
              (loop []
                (when-let [r (bdecode @buf 0)]
                  (reset! buf (.subarray @buf (aget r 1)))
                  (doseq [h @handlers] (h (aget r 0)))
                  (recur)))))
       (.on sock "connect"
            (fn [] (resolve #js {:sock sock :handlers handlers})))))))

(defn nrepl-request
  "Send an nREPL op, resolve with the vector of response messages once a `done`
  status arrives for this id."
  [client op]
  (js/Promise.
   (fn [resolve _reject]
     (let [id (str (js/Math.random))
           msgs #js []]
       (aset op "id" id)
       (swap! (.-handlers client) conj
              (fn [msg]
                (when (= (aget msg "id") id)
                  (.push msgs msg)
                  (when-let [st (aget msg "status")]
                    (when (.includes st "done")
                      (resolve msgs))))))
       (.write (.-sock client) (bencode op))))))

(defn msg-field
  "First non-nil value of string key `k` across the response messages."
  [msgs k]
  (some (fn [m] (aget m k)) (js/Array.from msgs)))

(defn nrepl-eval
  ([client session code] (nrepl-eval client session code nil))
  ([client session code ns]
   (-> (nrepl-request client (cond-> #js {:op "eval" :session session :code code}
                               ns (doto (aset "ns" ns))))
       (.then (fn [msgs]
                (some (fn [m] (aget m "value")) (js/Array.from msgs)))))))

;; --------------------------------------------------------------- helpers ----

(defn with-timeout
  "Reject with a labeled error if `p` doesn't settle within `ms`. Keeps the test
  from hanging forever (e.g. in CI) when something never becomes ready."
  [ms label p]
  (js/Promise.race
   #js [p
        (js/Promise.
         (fn [_ reject]
           (js/setTimeout
            (fn [] (reject (js/Error. (str "timeout after " ms "ms waiting for: " label))))
            ms)))]))

(defn wait-output
  "Resolve when `substr` appears in the stream's accumulated output. Accumulates
  (so a match split across chunks still works) and matches a raw substring (so
  ANSI color codes vite emits in CI don't break it - we match the port number,
  which stays contiguous between color codes)."
  [stream substr]
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

(defn check [label expected actual]
  (if (= expected actual)
    (println "PASS:" label)
    (do (swap! failures inc)
        (println "FAIL:" label "- expected" (pr-str expected) "got" (pr-str actual)))))

(defn run-to-exit [proc]
  (js/Promise. (fn [resolve _] (.on proc "exit" (fn [code] (resolve code))))))

(defn ^:async check-counter
  "Assert the counter widget at `sel` renders 'Counted: 0', then clicking its
  button increments to 'Counted: 1'. Same behaviour, three rendering models."
  [page name sel]
  (let [text #(.textContent page sel)]
    (await (with-timeout 15000 (str name " render")
                         (.waitForFunction page (str "document.querySelector('" sel "') && document.querySelector('" sel "').textContent.includes('Counted: 0')"))))
    (check (str name " rendered") true (str/includes? (await (text)) "Counted: 0"))
    (await (.click page (str sel " button")))
    (await (with-timeout 10000 (str name " click")
                         (.waitForFunction page (str "document.querySelector('" sel "').textContent.includes('Counted: 1')"))))
    (check (str name " click increments") true (str/includes? (await (text)) "Counted: 1"))))

(defn ^:async check-build []
  ;; `vite build` should emit a regular, optimizable bundle (no REPL/HMR output).
  (let [code (await (run-to-exit (.spawn cp "node_modules/.bin/vite" #js ["build"]
                                         #js {:cwd EXDIR :stdio "ignore"})))]
    (check "vite build exit 0" 0 code)
    (let [adir (path/join EXDIR "dist" "assets")
          files (try (.readdirSync fs adir) (catch :default _ #js []))
          js (some (fn [f] (when (str/ends-with? f ".js") f)) (js/Array.from files))]
      (check "build emitted a JS bundle" true (some? js))
      (when js
        (let [content (.readFileSync fs (path/join adir js) "utf8")]
          (check "bundle has no HMR" false (str/includes? content "import.meta.hot")))))))

(defn ^:async check-macro-reload
  "Issue #819: with the persistent vite compiler running, editing a macro file
  and re-saving the file that uses it must surface the NEW macro output, not the
  first-loaded definition (a regression where the SCI instance cached macros)."
  [page macro-file consumer-file orig-consumer]
  (await (with-timeout 15000 "macro-tag v1"
                       (.waitForFunction page "document.querySelector('#macro-tag') && document.querySelector('#macro-tag').textContent.includes('macro-v1')")))
  (check "macro initial render (v1)" true
         (str/includes? (await (.textContent page "#macro-tag")) "macro-v1"))
  ;; edit the macro, then touch the consuming source so vite recompiles it and
  ;; re-runs the macro scan (the .cljc isn't in the JS module graph, so only the
  ;; consumer edit triggers a recompile - matches the accepted #819 workflow)
  (.writeFileSync fs macro-file "(ns e2e-macros)\n(defmacro tag [] \"macro-v2\")\n")
  (.writeFileSync fs consumer-file (str orig-consumer "\n;; touched by e2e\n"))
  (await (with-timeout 20000 "macro-tag v2 (HMR after macro edit)"
                       (.waitForFunction page "document.querySelector('#macro-tag') && document.querySelector('#macro-tag').textContent.includes('macro-v2')")))
  (check "macro edit picked up across recompile (#819)" true
         (str/includes? (await (.textContent page "#macro-tag")) "macro-v2")))

;; ------------------------------------------------------------------ run ----

;; Hard safety net: never let the process hang past 2 min (unref'd so a fast
;; success can still exit immediately).
(.unref (js/setTimeout
         (fn [] (println "HARD TIMEOUT: test exceeded 120s") (js/process.exit 1))
         120000))

(defn ^:async run []
  (await (check-build))
  (let [vite (.spawn cp "node_modules/.bin/vite" #js ["dev" "--port" (str PORT)]
                     #js {:cwd EXDIR
                          :env (js/Object.assign #js {} js/process.env
                                                 #js {"SQUINT_NREPL_PORT" (str NREPL-PORT)})})
        browser (atom nil)
        ;; #819 fixture: macro file + the source that uses it. Capture originals
        ;; so we can restore after editing them at runtime.
        macro-file (path/join EXDIR "src" "e2e_macros.cljc")
        consumer-file (path/join EXDIR "src" "plain.cljs")
        orig-macro (.readFileSync fs macro-file "utf8")
        orig-consumer (.readFileSync fs consumer-file "utf8")
        restore-files! (fn [] (try (.writeFileSync fs macro-file orig-macro)
                                   (.writeFileSync fs consumer-file orig-consumer)
                                   (catch :default _ nil)))
        ;; safety net: the hard-timeout path calls process.exit and skips the
        ;; finally below, so restore on exit too (sync, idempotent)
        _ (.on js/process "exit" restore-files!)
        ;; capture everything for diagnostics on failure (CI has no live view)
        vlog (atom "")
        log! (fn [& xs] (swap! vlog str (apply str xs) "\n"))]
    (.on (.-stdout vite) "data" (fn [d] (swap! vlog str d)))
    (.on (.-stderr vite) "data" (fn [d] (swap! vlog str d)))
    (.on vite "error" (fn [e] (log! "[vite spawn error] " (.-message e))))
    (.on vite "exit" (fn [code] (log! "[vite exited] code " code)))
    (try
      ;; match the port (contiguous between vite's ANSI color codes), not "Local:"
      (await (with-timeout 45000 "vite ready" (wait-output (.-stdout vite) (str PORT))))
      (reset! browser (await (with-timeout 30000 "chromium launch"
                                           (.launch (.-chromium pw) #js {:headless true}))))
      (let [page (await (.newPage @browser))
            _ (.on page "pageerror" (fn [e] (log! "[pageerror] " (.-message e))))
            _ (.on page "console" (fn [m] (when (= "error" (.type m)) (log! "[browser console.error] " (.text m)))))
            ready (wait-console page "nrepl listener ready")]
        (await (.goto page URL))
        (await (with-timeout 30000 "browser nrepl listener ready" ready))
        ;; The three :main entries each render a counter (Counted: 0 + button)
        ;; via a different model: plain squint #html, preact #jsx (useState),
        ;; reagami hiccup. Each must render and increment on click.
        (await (check-counter page "plain (#html)" "#plain"))
        (await (check-counter page "preact (#jsx)" "#preact"))
        (await (check-counter page "reagami (hiccup)" "#reagami"))
        (await (check-macro-reload page macro-file consumer-file orig-consumer))
        (let [client (await (with-timeout 10000 "nrepl connect" (make-client NREPL-PORT)))
              clone (await (with-timeout 10000 "nrepl clone" (nrepl-request client #js {:op "clone"})))
              session (some (fn [m] (aget m "new-session")) (js/Array.from clone))
              ev (fn [code] (with-timeout 20000 (str "eval " (pr-str code))
                                          (nrepl-eval client session code)))]
          ;; define a var in a real example ns (`ui`), read it from another ns
          ;; via the alias. Values come back pr-str'd (a string prints quoted).
          (await (ev "(ns ui) (def xns \"v1\")"))
          (check "cross-ns read"
                 "\"v1\""
                 (await (ev "(ns repltest (:require [ui :as u])) u/xns")))
          ;; redefine it in `ui`, the cross-ns ref must see the new value
          (await (ev "(ns ui) (def xns \"v2\")"))
          (check "cross-ns redef visible"
                 "\"v2\""
                 (await (ev "(ns repltest (:require [ui :as u])) u/xns")))
          ;; ns honored + shared ns-state: eval in reagami-app (via the request
          ;; :ns, no (ns ...) form) referencing `state`, which is defined in
          ;; reagami_app.cljs (compiled at page load, never redefined this
          ;; session). Resolves to globalThis.reagami_app.state (was "state is
          ;; not defined", and the eval used to run in `user`).
          (check "file-defined var visible at REPL"
                 "true"
                 (await (with-timeout 20000 "eval in reagami-app"
                                      (nrepl-eval client session "(map? @state)" "reagami-app"))))
          ;; #jsx eval'd at the REPL must compile to jsx() calls (not raw <tags>,
          ;; which the browser can't eval) and render into the live page
          (await (ev (str "(ns repljsx (:require [\"preact\" :refer [render]]))"
                          " (render #jsx [:div \"jsx-repl-ok\"] (js/document.querySelector \"#preact\"))")))
          (check "REPL #jsx renders"
                 "jsx-repl-ok"
                 (await (.textContent page "#preact")))
          ;; info / eldoc / complete (issue #832): the server reads arglists/doc
          ;; and completion candidates from the compiler ns-state, populated by
          ;; the server-side compile that every eval runs (even browser evals).
          (await (ev "(ns infotest) (defn add2 \"adds two\" [a b] (+ a b))"))
          (let [info (await (with-timeout 10000 "nrepl info"
                                          (nrepl-request client #js {:op "info" :sym "add2" :ns "infotest"})))]
            (check "info arglists-str" "[a b]" (msg-field info "arglists-str"))
            (check "info doc" "adds two" (msg-field info "doc")))
          (let [eldoc (await (with-timeout 10000 "nrepl eldoc"
                                           (nrepl-request client #js {:op "eldoc" :sym "add2" :ns "infotest"})))]
            (check "eldoc docstring" "adds two" (msg-field eldoc "docstring")))
          (let [cpl (await (with-timeout 10000 "nrepl complete"
                                         (nrepl-request client #js {:op "complete" :prefix "add" :ns "infotest"})))
                cands (msg-field cpl "completions")
                names (when cands (map (fn [c] (aget c "candidate")) (js/Array.from cands)))]
            (check "complete finds add2" true (boolean (some (fn [n] (= "add2" n)) names))))
          ;; unknown symbol must reply no-info, not hang (the bug in #832)
          (let [unknown (await (with-timeout 10000 "nrepl info unknown"
                                             (nrepl-request client #js {:op "info" :sym "nope-nope" :ns "infotest"})))
                st (msg-field unknown "status")]
            (check "info unknown -> no-info" true (boolean (and st (.includes st "no-info")))))))
      (catch :default e
        (swap! failures inc)
        (println "ERROR:" (.-message e))
        (println "----- vite / browser output -----")
        (println @vlog)
        (println "---------------------------------"))
      (finally
        (restore-files!)
        (when @browser (try (await (.close @browser)) (catch :default _ nil)))
        (.kill vite)))
    (println (if (zero? @failures) "\nAll checks passed." (str "\n" @failures " failure(s).")))
    (js/process.exit (if (zero? @failures) 0 1))))

(run)
