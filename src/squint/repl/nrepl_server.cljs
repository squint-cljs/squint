(ns squint.repl.nrepl-server
  (:require
   [clojure.string :as str]
   [cljs.pprint :as pp]
   ["fs" :as fs]
   ["net" :as node-net]
   [squint.compiler-common :as cc]
   [squint.compiler :as compiler]
   [squint.repl.nrepl.bencode :refer [decode-all encode]]
   ["ws" :refer [WebSocketServer]]))

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
  handler)

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

;; TODO: this should not be global
(def last-ns (atom cc/*cljs-ns*))

(def pretty-print-fns-map
  {"cider.nrepl.pprint/pprint" pp/write})

(defn format-value [nrepl-pprint pprint-options value]
  (if nrepl-pprint
    (if-let [pprint-fn (pretty-print-fns-map nrepl-pprint)]
      (let [{:keys [right-margin length level]} pprint-options]
        (binding [*print-length* length
                  *print-level* level
                  pp/*print-right-margin* right-margin]
          (with-out-str (pprint-fn value))))
      (do
        (debug "Pretty-Printing is only supported for cider.nrepl.pprint/pprint")
        (pr-str value)))
    (pr-str value)))

(defn send-value [request send-fn v]
  (let [[v opts] v
        sci-ns (:ns opts)]
    (reset! last-ns sci-ns)
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
                      "ns" (str cc/*cljs-ns*)})))

(def in-progress (atom false))
(def state (atom nil))

(defn compile [the-val]
  (let [{js-str :javascript
         cljs-ns :ns
         :as new-state} (compiler/compile-string* the-val {:context :return
                                                           :elide-exports true
                                                           :elide-imports true
                                                           :repl true
                                                           :async true}
                                                  @state)
        _ (reset! state new-state)
        js-str (str/replace "(async function () {\n%s\n}) ()" "%s" js-str)]
    (reset! last-ns cljs-ns)
    js-str))

(def !ws-conn (atom nil))
(def !target (atom nil))

(defn do-handle-eval [{:keys [ns code file
                              _load-file? _line] :as request} send-fn]
  (->
   (js/Promise.resolve code)
   (.then compile)
   (.then (fn [v]
            (println "About to eval:")
            (println v)
            (if (= :browser @!target)
              (if-let [conn @!ws-conn]
                (.send conn (js/JSON.stringify
                             #js {:op "eval"
                                  :code v}))
                ;; TODO: here comes the websocket code
                )
              (js/eval v))))
   (.then (fn [val]
            (send-fn request {"ns" (str @last-ns)
                              "value" (format-value (:nrepl.middleware.print/print request)
                                                    (:nrepl.middleware.print/options request)
                                                    val)})))
   (.catch (fn [e]
             (js/console.error e)
             (handle-error send-fn request e)))
   (.finally (fn []
               (send-fn request {"ns" (str @last-ns)
                                 "status" ["done"]})))))

(defn handle-eval [{:keys [ns] :as request} send-fn]
  (prn :ns ns)
  (do-handle-eval (assoc request :ns @last-ns)
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

(defn handle-lookup [{:keys [ns] :as request} send-fn]
  #_(let [mapping-type (-> request :op)]
      (try
        (let [ns-str (:ns request)
              sym-str (or (:sym request) (:symbol request))
              sci-ns
              (or (when ns
                    (the-sci-ns (store/get-ctx) (symbol ns)))
                  @last-ns
                  @sci/ns)]
          (sci/binding [sci/ns sci-ns]
            (let [m (sci/eval-string* (store/get-ctx) (gstring/format "
(let [ns '%s
      full-sym '%s]
  (when-let [v (ns-resolve ns full-sym)]
    (let [m (meta v)]
      (assoc m :arglists (:arglists m)
       :doc (:doc m)
       :name (:name m)
       :ns (some-> m :ns ns-name)
       :val @v))))" ns-str sym-str))
                  doc (:doc m)
                  file (:file m)
                  line (:line m)
                  reply (case mapping-type
                          :eldoc (cond->
                                     {"ns" (:ns m)
                                      "name" (:name m)
                                      "eldoc" (mapv #(mapv str %) (:arglists m))
                                      "type" (cond
                                               (ifn? (:val m)) "function"
                                               :else "variable")
                                      "status" ["done"]}
                                   doc (assoc "docstring" doc))
                          (:info :lookup) (cond->
                                              {"ns" (:ns m)
                                               "name" (:name m)
                                               "arglists-str" (forms-join (:arglists m))
                                               "status" ["done"]}
                                            doc (assoc "doc" doc)
                                            file (assoc "file" file)
                                            line (assoc "line" line)))]
              (send-fn request reply))))
        (catch js/Error e
          (let [status (cond->
                           #{"done"}
                         (= mapping-type :eldoc)
                         (conj "no-eldoc"))]
            (send-fn
             request
             {"status" status "ex" (str e)}))))))

(defn handle-load-file [{:keys [file] :as request} send-fn]
  #_(do-handle-eval (assoc request
                           :code file
                           :load-file? true
                           :ns @sci/ns)
                    send-fn))

;;;; Completions, based on babashka.nrepl

(defn handle-complete [request send-fn]
  #_(send-fn request (utils/handle-complete* request)))

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
         (let [port (or (:port opts)
                        0)
               host (or (:host opts)
                        "127.0.0.1" ;; default
                        )
               target (or (some-> (:target opts)
                                  keyword)
                          :node)
               _log_level (or (if (object? opts)
                                (.-log_level ^Object opts)
                                (:log_level opts))
                              "info")
               server (node-net/createServer
                       (partial on-connect {}))]
           (reset! !target target)
           ;; Expose "app" key under js/app in the repl
           (when (= :browser target)
             (let [wss (new WebSocketServer #js {:port 1340})]
               (println "Websocket server running on port 1340")
               (.on wss "connection" (fn [conn]
                                       (reset! !ws-conn conn)
                                       #_(.on conn "message"
                                              (fn [data]
                                                (js/console.log "data" data)))
                                       #_(.send conn "something")))
               #_(reset! !ws-server wss)))
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
