(ns squint.repl.nrepl-server
  (:require
   [clojure.string :as str]
   [cljs.pprint :as pp]
   ["fs" :as fs]
   ["net" :as node-net]
   [squint.compiler-common :as cc]
   [squint.compiler :as compiler]
   [squint.compiler.node :as compiler-node]
   [squint.internal.node.utils :as utils]
   [squint.repl.nrepl.bencode :refer [decode-all encode]]
   [squint.repl.print :as rp]))

(defn debug [& strs]
  (.debug js/console (str/join " " strs)))

(defn warn [& strs]
  (.warn js/console (str/join " " strs)))

(defn info [& strs]
  (.info js/console (str/join " " strs)))

(defn response-for-mw [handler]
  (fn [{:keys [id session] :as request} response]
    (let [response (cond-> (assoc response
                                  "id" id)
                     session (assoc "session" session))]
      (handler request response))))

(defn coerce-request-mw [handler]
  (fn [request send-fn]
    (handler (update request :op keyword) send-fn)))

(defn log-request-mw [handler]
  (fn [request send-fn]
    (debug "request" request)
    (handler request send-fn)))

(defn log-response-mw [handler]
  (fn [request response]
    (debug "response" response)
    (handler request response)))

(defn eval-ctx-mw [handler _]
  (fn [request send-fn]
    (handler request
             send-fn)))

(declare ops)

(defn version-string->data [v]
  (assoc (zipmap ["major" "minor" "incremental"]
                 (js->clj (.split v ".")))
         "version-string" v))

(defn handle-describe [request send-fn]
  (send-fn request
           {"versions" {"nbb-nrepl" (version-string->data "TODO")
                        "node" (version-string->data js/process.version)}
            "aux" {}
            "ops" (zipmap (map name (keys ops)) (repeat {}))
            "status" ["done"]}))

;; Result of the last compile (carries :ns-state, threaded back into the next
;; compile via the `state` arg, so the compiler ns-state persists across evals).
(def state (atom nil))

;; Shared compiler ns-state. A host (e.g. the vite plugin) can inject the same
;; ns-state atom it threads through its file compiles, so the REPL knows the
;; vars/aliases those files defined (e.g. a `def` that ran at page load). cljs
;; atoms survive the js->clj/clj->js round-trip as opaque objects, so this can
;; be passed across the JS boundary. nil -> the REPL keeps its own ns-state.
(def !ns-state (atom nil))

(defn current-ns
  "Current REPL namespace = :current of the persistent compiler ns-state (the
  one the last compile used, carried in `state`). Updated by compiles via the
  ns handler / compile* seed. Falls back to a host-injected ns-state before the
  first eval, then to 'user."
  []
  (or (some-> @state :ns-state deref :current)
      (some-> @!ns-state deref :current)
      'user))

(def pretty-print-fns-map
  {"cider.nrepl.pprint/pprint" pp/write})

(defn format-value [nrepl-pprint pprint-options value]
  (if nrepl-pprint
    (if-let [pprint-fn (pretty-print-fns-map nrepl-pprint)]
      (let [{:keys [right-margin length level]} pprint-options]
        (binding [*print-length* length
                  *print-level* level
                  pp/*print-right-margin* right-margin]
          (js/Promise.resolve (with-out-str (pprint-fn value)))))
      (do
        (debug "Pretty-Printing is only supported for cider.nrepl.pprint/pprint")
        (rp/pr-str-repl value)))
    ;; rp/pr-str-repl: squint's pr-str + Promise-aware rendering so an
    ;; unresolved Promise prints as `#<Promise ..>` instead of `{}`.
    (rp/pr-str-repl value)))

(defn send-value [request send-fn v]
  (let [[v opts] v
        sci-ns (:ns opts)]
    (let [v (format-value (:nrepl.middleware.print/print request)
                          (:nrepl.middleware.print/options request)
                          v)]
      (send-fn request {"value" v
                        "ns" (str sci-ns)}))))

(defn handle-error [send-fn request e]
  (let [data (ex-data e)]
    (when-let [message (or (:message data) (.-message e))]
      (send-fn request {"err" (str message "\n")}))
    (send-fn request {"ex" (str e)
                      "ns" (str (current-ns))})))

(def in-progress (atom false))

;; Transport seam. By default the server evaluates compiled JS locally (node).
;; A host (e.g. a vite plugin) can inject a browser transport via start-server's
;; :browser-transport opt: compiled JS is sent to the browser and the result is
;; awaited, correlated by request id.
(def !browser-send (atom nil)) ;; (fn [#js {:op :code :id :session}] -> void)
(def !browser-url (atom nil))  ;; (fn [] -> dev server URL string), for the timeout hint
(def !pending (atom {}))       ;; request id -> #js {:resolve _ :reject _}
(def browser-eval-timeout-ms 8000)

(defn handle-browser-message
  "Feed a browser reply back into the server, resolving the awaiting request.
  `msg` is a JS object (or JSON string) {op, value/completions, ex, id, session}.
  The stored resolve fn receives the whole msg and extracts what it needs (eval
  takes :value, complete-js takes :completions). Exposed so a transport can
  deliver browser replies."
  [msg]
  (let [msg (if (string? msg) (js/JSON.parse msg) msg)
        id (.-id ^js msg)
        p (get @!pending id)]
    (when p
      (swap! !pending dissoc id)
      (if-some [ex (.-ex ^js msg)]
        ((.-reject ^js p) (js/Error. (str ex)))
        ((.-resolve ^js p) msg)))))

(defn compile [the-val ns]
  (let [;; Apply squint.edn's :jsx-runtime so #jsx eval'd at the REPL emits
        ;; jsx() calls (not raw <tags>, which the browser can't eval). The REPL
        ;; is always dev, so use the jsx-dev-runtime.
        jsx-runtime (some-> (utils/get-cfg) :jsx-runtime (assoc :development true))
        ;; evaluate in the ns the editor asked for (e.g. the buffer's ns), so a
        ;; form like (defn render ...) lands in that ns - not whatever was last
        ;; evaluated. A form's own (ns ...) still switches from there.
        ns (when ns (symbol ns))
        {js-str :javascript
         :as new-state} (compiler/compile-string* the-val
                                                  (cond-> {;; :repl-return wraps the top-level value in [v]; the
                                                           ;; eval handler unboxes. This keeps a Promise the user
                                                           ;; returned from being auto-unwrapped by the async IIFE
                                                           ;; (so e.g. `(js/Promise.resolve 1)` prints as a Promise).
                                                           :context :repl-return
                                                           :elide-exports true
                                                           :repl true
                                                           :async true
                                                           ;; Node eval imports by absolute path. The
                                                           ;; browser transport loads over HTTP and sets
                                                           ;; globalThis, so leave its requires bare.
                                                           :resolve-ns (when-not @!browser-send
                                                                         compiler-node/resolve-ns-repl)}
                                                    jsx-runtime (assoc :jsx-runtime jsx-runtime)
                                                    ;; share the host's ns-state so file-defined
                                                    ;; vars/aliases are visible to the REPL
                                                    @!ns-state (assoc :ns-state @!ns-state)
                                                    ns (assoc :ns ns))
                                                  @state)
        _ (reset! state new-state)
        ;; ensure there's always a box to unwrap, even for forms with no
        ;; top-level return (e.g. a lone `(ns ...)`). The user's return-with-box,
        ;; when emitted, runs first and the appended line is unreachable.
        js-str (str js-str "\n;return [undefined];")
        js-str (cc/replace-first* "(async function () {\n%s\n}) ()" "%s" js-str)]
    js-str))

(defn node-eval
  "Default evaluator: eval compiled JS locally and format the value."
  [js-str request]
  (-> (js/Promise.resolve (js/eval js-str))
      (.then (fn [^js boxed]
               ;; compile wraps the user's top-level value in [v] so a Promise
               ;; survives the async IIFE without being auto-unwrapped
               (let [val (aget boxed 0)]
                 (format-value (:nrepl.middleware.print/print request)
                               (:nrepl.middleware.print/options request)
                               val))))))

(defn browser-eval
  "Evaluator that delegates to a browser over the injected transport, awaiting
  the result correlated by request id."
  [js-str request]
  (js/Promise.
   (fn [resolve reject]
     (let [id (:id request)]
       ;; handle-browser-message now resolves with the whole reply msg; pull the value.
       (swap! !pending assoc id #js {:resolve (fn [msg] (resolve (.-value ^js msg)))
                                     :reject reject})
       ;; Don't hang forever if no browser is connected (or it never replies):
       ;; the eval runs *in the page*, so without an open tab there's nothing to
       ;; run it. Reject with an actionable message.
       (js/setTimeout
        (fn []
          (when (get @!pending id)
            (swap! !pending dissoc id)
            (let [url (when-let [f @!browser-url] (f))
                  where (if url (str "Open " url " in a browser tab.")
                            "Open the dev server URL in a browser tab.")]
              (reject (js/Error. (str "No response from the browser within "
                                      browser-eval-timeout-ms "ms. "
                                      where " The REPL evaluates in the page."))))))
        browser-eval-timeout-ms)
       (if-let [send @!browser-send]
         (send #js {:op "eval"
                    :code js-str
                    :id id
                    :session (:session request)})
         (do (swap! !pending dissoc id)
             (reject (js/Error. "No browser transport connected"))))))))

(defn- own+proto-names
  "All property names of `obj` and its prototype chain (like what's reachable via
  dot access), as a JS array."
  [obj]
  (let [acc (js/Set.)]
    (loop [o obj]
      (when (some? o)
        (doseq [n (js/Object.getOwnPropertyNames o)]
          (.add acc n))
        (recur (js/Object.getPrototypeOf o))))
    (js/Array.from acc)))

(def js-complete-limit 100)

(defn js-completions
  "JS interop candidates for a `js/...` prefix, enumerated from `globalThis`.
  e.g. `js/Ma` -> [\"js/Math\" \"js/Map\" ...], `js/console.lo` -> [\"js/console.log\"].
  Returns a JS array of candidate strings (capped). Pure, runtime-agnostic: the
  browser listener (vite.js) mirrors this so completion reflects the page's
  globals; node uses it directly."
  [prefix]
  (if-not (str/starts-with? prefix "js/")
    #js []
    (let [s (subs prefix 3)
          parts (.split s ".")
          partial (aget parts (dec (.-length parts)))
          path (.slice parts 0 (dec (.-length parts)))
          obj (reduce (fn [o seg] (when (some? o) (aget o seg)))
                      js/globalThis
                      (js/Array.from path))]
      (if (nil? obj)
        #js []
        (let [pre (str "js/" (when (pos? (.-length path))
                               (str (.join path ".") ".")))]
          (->> (own+proto-names obj)
               (filter (fn [n] (str/starts-with? n partial)))
               (sort)
               (take js-complete-limit)
               (map (fn [n] (str pre n)))
               (into-array)))))))

(defn browser-js-complete
  "Ask the browser to enumerate JS-interop candidates for `prefix` (its globals
  differ from node's). Sends a `complete-js` op over the transport and resolves
  with a JS array of candidate strings; resolves to [] on timeout or no transport."
  [prefix request]
  (js/Promise.
   (fn [resolve _reject]
     (let [id (:id request)]
       (swap! !pending assoc id #js {:resolve (fn [msg] (resolve (or (.-completions ^js msg) #js [])))
                                     :reject (fn [_] (resolve #js []))})
       (js/setTimeout
        (fn []
          (when (get @!pending id)
            (swap! !pending dissoc id)
            (resolve #js [])))
        browser-eval-timeout-ms)
       (if-let [send @!browser-send]
         (send #js {:op "complete-js"
                    :prefix prefix
                    :id id
                    :session (:session request)})
         (do (swap! !pending dissoc id)
             (resolve #js [])))))))

(def !eval-fn (atom node-eval))

(defn evaluate
  "Compile `code` (cljs) and evaluate it via the active evaluator. Returns a
  Promise of the value (a printed string). Shared by the nREPL eval op and the
  JS entrypoint below."
  [code request]
  (-> (js/Promise.resolve code)
      (.then (fn [c] (compile c (:ns request))))
      (.then (fn [js-str] (@!eval-fn js-str request)))))

(defn evaluate-string
  "JS entrypoint for evaluating a single code string (e.g. a dev HTTP trigger).
  Returns Promise<#js {:value :ns}>; rejects on error."
  [code]
  (-> (evaluate code {:id (str (random-uuid)) :session "http"})
      (.then (fn [value] #js {:value value :ns (str (current-ns))}))))

(defn do-handle-eval [{:keys [ns code file
                              _load-file? _line] :as request} send-fn]
  (->
   (evaluate code request)
   (.then (fn [value]
            (send-fn request {"ns" (str (current-ns))
                              "value" value})))
   (.catch (fn [e]
             (js/console.error e)
             (handle-error send-fn request e)))
   (.finally (fn []
               (send-fn request {"ns" (str (current-ns))
                                 "status" ["done"]})))))

(defn handle-eval [{:keys [ns] :as request} send-fn]
  ;; evaluate in the ns the editor sent (the buffer's ns); fall back to the last
  ;; ns for clients that don't send one.
  (do-handle-eval (assoc request :ns (or ns (current-ns)))
                  send-fn))

(defn handle-clone [request send-fn]
  (send-fn request {"new-session" (str (random-uuid))
                    "status" ["done"]}))

(defn handle-close [request send-fn]
  (send-fn request {"status" ["done"]}))

;; compare [[babashka.nrepl.impl.server]]

(defn forms-join [forms]
  (->> (map pr-str forms)
       (str/join \newline)))

(defn ns-state-map
  "The live compiler ns-state map: prefer the one carried by the last compile
  (`state`), fall back to a host-injected ns-state. nil before the first eval
  with no host atom. See notes/internal/compiler-ns-state.md for its shape."
  []
  (or (some-> @state :ns-state deref)
      (some-> @!ns-state deref)))

(defn handle-lookup [{:keys [op ns] :as request} send-fn]
  ;; Resolve a var's metadata from the compiler ns-state (squint has no runtime
  ;; ns environment; arglists/doc are captured at compile time, keyed by the
  ;; unmunged symbol under [ns var-sym]). Only same-session, unqualified vars
  ;; resolve; core fns and external-lib vars are not tracked -> no-info.
  (let [sym (or (:sym request) (:symbol request))
        ns-sym (symbol (or ns (str (current-ns))))
        m (when (and sym (not (str/includes? sym "/")))
            (get-in (ns-state-map) [ns-sym (symbol sym)]))
        {:keys [arglists doc line file]} m]
    (if m
      (let [base {"ns" (str ns-sym) "name" (str sym) "status" ["done"]}
            reply (case op
                    :eldoc (cond-> (assoc base
                                          "eldoc" (mapv #(mapv str %) arglists)
                                          "type" (if arglists "function" "variable"))
                             doc (assoc "docstring" doc))
                    ;; :info :lookup
                    (cond-> base
                      arglists (assoc "arglists-str" (forms-join arglists))
                      doc (assoc "doc" doc)
                      file (assoc "file" file)
                      line (assoc "line" line)))]
        (send-fn request reply))
      (send-fn request {"status" (case op
                                   :eldoc ["no-eldoc" "done"]
                                   ["no-info" "done"])}))))

(defn handle-load-file [{:keys [file] :as request} send-fn]
  ;; nREPL load-file sends the file contents in `file`. Evaluate them through
  ;; the normal eval path so the client gets a value + done response.
  (do-handle-eval (assoc request :code file :load-file? true)
                  send-fn))

;;;; Completions

(defn handle-complete [{:keys [ns] :as request} send-fn]
  ;; Two candidate sources, merged:
  ;; - cljs: the compiler ns-state for the current ns (locally defined vars,
  ;;   :refers, :aliases). Core fns / external-lib publics are not tracked.
  ;;   Munged self-aliases (e.g. clojure_DOT_string) are dropped as noise.
  ;; - js: for a `js/...` prefix, JS-interop names enumerated from globalThis -
  ;;   in the browser when eval is delegated there (its globals differ), else
  ;;   from node directly.
  (let [prefix (or (:prefix request) (:symbol request) "")
        ns-sym (symbol (or ns (str (current-ns))))
        cur (get (ns-state-map) ns-sym)
        names (concat (filter symbol? (keys cur))
                      (keys (:refers cur))
                      (keys (:aliases cur)))
        cljs-cands (->> names
                        (map str)
                        (remove #(str/includes? % "_DOT_"))
                        (filter #(str/starts-with? % prefix)))
        js-prefix? (str/starts-with? prefix "js/")
        js-promise (cond
                     (not js-prefix?) (js/Promise.resolve #js [])
                     @!browser-send (browser-js-complete prefix request)
                     :else (js/Promise.resolve (js-completions prefix)))]
    (-> js-promise
        (.then (fn [js-cands]
                 (let [cands (->> (concat cljs-cands (js/Array.from js-cands))
                                  distinct
                                  sort
                                  (mapv (fn [c] {"candidate" c})))]
                   (send-fn request {"completions" cands "status" ["done"]}))))
        (.catch (fn [_]
                  (send-fn request {"completions" (mapv (fn [c] {"candidate" c}) (sort cljs-cands))
                                    "status" ["done"]}))))))

;;;; End completions

(def ops
  "Operations supported by the nrepl server"
  {:eval handle-eval
   :describe handle-describe
   :info handle-lookup
   :lookup handle-lookup
   :eldoc handle-lookup
   :clone handle-clone
   :close handle-close
   ;; :macroexpand handle-macroexpand
   ;; :classpath handle-classpath
   :load-file handle-load-file
   :complete handle-complete})

(defn handle-request [{:keys [op] :as request} send-fn]
  (if-let [op-fn (get ops op)]
    (op-fn request send-fn)
    (do
      (warn "Unhandled operation" op)
      (send-fn request {"status" ["error" "unknown-op" "done"]}))))

(defn make-request-handler [opts]
  (-> handle-request
      coerce-request-mw
      (eval-ctx-mw opts)
      log-request-mw))

(defn make-send-fn [socket]
  (fn [_request response]
    (.write socket (encode response))))

(defn make-reponse-handler [socket]
  (-> (make-send-fn socket)
      log-response-mw
      response-for-mw))

(defn on-connect [opts socket]
  (debug "Connection accepted")
  (.setNoDelay ^node-net/Socket socket true)
  (let [handler (make-request-handler opts)
        response-handler (make-reponse-handler socket)
        pending (atom nil)]
    (.on ^node-net/Socket socket "data"
         (fn [data]
           (let [data (if-let [p @pending]
                        (let [s (str p data)]
                          (reset! pending nil)
                          s)
                        data)
                 [requests unprocessed] (decode-all data :keywordize-keys true)]
             (when (not (str/blank? unprocessed))
               (reset! pending unprocessed))
             (doseq [request requests]
               (handler request response-handler))))))
  (.on ^node-net/Socket socket "close"
       (fn [had-error?]
         (if had-error?
           (debug "Connection lost")
           (debug "Connection closed")))))

(def !server (atom nil))

(defn start-server
  "Start nRepl server. Accepts options either as JS object or Clojure map."
  [opts]
  (-> (js/Promise.resolve nil)
      (.then
       (fn []
         (let [port (or (if (object? opts) (.-port ^js opts) (:port opts))
                        0)
               host (or (if (object? opts) (.-host ^js opts) (:host opts))
                        "127.0.0.1" ;; default
                        )
               _log_level (or (if (object? opts)
                                (.-log_level ^js opts)
                                (:log_level opts))
                              "info")
               browser-transport (or (:browser-transport opts)
                                     (when (object? opts)
                                       (.-browserTransport ^js opts)))
               ns-state (or (:ns-state opts)
                            (when (object? opts) (.-nsState ^js opts)))
               server (node-net/createServer
                       (partial on-connect {}))]
           ;; share the host's compiler ns-state (file compiles + REPL eval)
           (when ns-state (reset! !ns-state ns-state))
           ;; Pick the evaluator: delegate to a browser when a transport was
           ;; injected, otherwise eval locally (node).
           (if browser-transport
             (do (reset! !browser-send (.-send ^js browser-transport))
                 ;; optional: a fn returning the dev server URL, for the timeout hint
                 (reset! !browser-url (.-url ^js browser-transport))
                 (reset! !eval-fn browser-eval))
             (reset! !eval-fn node-eval))
           (.listen server
                    port
                    host
                    (fn []
                      (let [addr (-> server (.address))
                            port (-> addr .-port)
                            host (-> addr .-address)]
                        (println (str "nREPL server started on port " port " on host " host " - nrepl://" host ":" port))
                        ;; `println` does not normally emit a final
                        ;; newline in cljs because it is redirected to
                        ;; the `js/console` which explicitly adds
                        ;; it. This behavior is controlled by the
                        ;; dynamic variable
                        ;; `cljs.core/*print-newline*` which
                        ;; `sci/print-newline` is set to.
                        ;;
                        ;; When sending output to an nREPL client, the
                        ;; `do-handle-eval` bindings set the
                        ;; corresponding var to true, so that a final
                        ;; newline is sent to the client. However, if
                        ;; the client does a `println` in an async
                        ;; call such as `js/setTimeout`, the old
                        ;; binding is used and no newline is sent to
                        ;; the client.
                        ;;
                        ;; As a workaround, the dynamic root binding
                        ;; is changed so that when the nREPL is up, a
                        ;; final newline is always sent with
                        ;; `println`.
                        #_(sci/alter-var-root sci/print-newline (constantly true))
                        (try
                          (.writeFileSync fs ".nrepl-port" (str port))
                          (catch :default e
                            (warn "Could not write .nrepl-port" e))))))
           (reset! !server server))))
      #_(let [onExit (js/require "signal-exit")]
          (onExit (fn [_code _signal]
                    (debug "Process exit, removing .nrepl-port")
                    (fs/unlinkSync ".nrepl-port"))))))

(defn stop-server!
  ([] (stop-server! @!server))
  ([_server]))
