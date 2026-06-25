(ns squint.repl.node
  (:require
   ["node:net" :as net]
   ["node:readline" :as readline]
   ["squint-cljs/core.js" :as squint]
   [clojure.string :as str]
   [edamame.core :as e]
   [squint.compiler :as compiler]
   [squint.compiler.node :as compiler-node]
   [squint.compiler-common :as cc]
   [squint.repl.print :as rp]))

(def pending-input (atom ""))

(declare input-loop eval-next)

(def in-progress (atom false))

(def last-ns (atom 'user))

(def rl-closed (atom false))

(defn continue [rl socket]
  (reset! in-progress false)
  (when-not @rl-closed
    (.setPrompt ^js rl (str @last-ns "=> "))
    (.prompt rl)
    (when-not (str/blank? @pending-input)
      (eval-next socket rl))))

(defn erase-processed [rdr]
  (let [line (e/get-line-number rdr)
        col (e/get-column-number rdr)
        lines (str/split-lines @pending-input)
        [line & lines] (drop (dec line) lines)
        edited (when line (subs line col))]
    (reset! pending-input (str/join "\n" (cons edited lines)))))

(def tty (and js/process.stdout.isTTY
              js/process.stdin.setRawMode))

(def state (atom nil))

(defn compile [the-val rl socket]
  (let [{js-str :javascript
         cljs-ns :ns
         :as new-state} (compiler/compile-string* (binding [*print-meta* true]
                                                    (pr-str the-val))
                                                  ;; :repl-return wraps the top-level value in [v] so a
                                                  ;; Promise the user returned survives the async IIFE
                                                  ;; (the eval handler unboxes); same shape as the nREPL
                                                  ;; server uses.
                                                  {:context :repl-return
                                                   :elide-exports true
                                                   :repl true
                                                   :async true
                                                   :resolve-ns compiler-node/resolve-ns-repl
                                                   :ns @last-ns}
                                                  @state)
        _ (reset! state new-state)
        ;; ensure there's always a box to unwrap (lone `(ns ..)` emits no return)
        js-str (str js-str "\n;return [undefined];")
        js-str (cc/replace-first* "(async function () {\n%s\n}) ()" "%s" js-str)]
    (reset! last-ns cljs-ns)
    #_(binding [*print-fn* *print-err-fn*]
        (println "---")
        (println js-str)
        (println "---"))
    (->
     (js/Promise.resolve (js/eval js-str))
     (.then (fn [^js boxed]
              (let [val (aget boxed 0)]
                (-> (rp/pr-str-repl val)
                    (.then (fn [s]
                             (if socket
                               (.write socket s "\n")
                               (js/console.log s))
                             (eval-next socket rl)))))))
     (.catch (fn [err]
               (squint/println err)
               (continue rl socket))))))

(defn eval-next [socket rl]
  (when (or @in-progress (not (str/blank? @pending-input)))
    (reset! in-progress true)
    (let [rdr (e/reader @pending-input)
          the-val (try (e/parse-next rdr compiler/squint-parse-opts)
                       (catch :default e
                         (if (str/includes? (ex-message e) "EOF while reading")
                           ::eof-while-reading
                           (do (erase-processed rdr)
                               (prn (str e))
                               ::continue))))]
      (cond (= ::continue the-val)
            (continue rl socket)
            (= ::eof-while-reading the-val)
            ;; more input expected
            (reset! in-progress false)
            :else
            (do (erase-processed rdr)
                (if-not (= :edamame.core/eof the-val)
                  (compile the-val rl socket)
                  (continue rl socket) #_(reset! in-progress false)))))))

(defn input-handler [socket rl input]
  (swap! pending-input str input "\n")
  ;; only when idle, the in-flight compile drains pending-input itself
  (when-not @in-progress
    (eval-next socket rl)))

(defn on-line [^js rl socket]
  (.on rl "line" #(input-handler socket rl %)))

(defn create-rl []
  (.createInterface
   readline #js {:input js/process.stdin
                 :output js/process.stdout}))

(defn create-socket-rl [socket]
  (.createInterface
   readline #js {:input socket
                 :output socket}))

(defn input-loop [socket resolve]
  (let [rl (if socket
             (create-socket-rl socket)
             (create-rl))]
    (on-line rl socket)
    (.setPrompt rl (str @last-ns "=> "))
    (.on rl "close" (fn []
                      (reset! rl-closed true)
                      (resolve)))
    (.prompt rl)))

(defn on-connect [socket]
  (let [rl (create-socket-rl socket)]
    (on-line rl socket))
  (.setNoDelay ^net/Socket socket true)
  (.on ^net/Socket socket "close"
       (fn [_had-error?]
         (println "Client closed connection."))))

(defn socket-repl
  ([] (socket-repl nil))
  ([opts]
   (reset! last-ns 'user)
   (let [port (or (:port opts)
                  0)
         srv (net/createServer
              on-connect)]
     (.listen srv port "127.0.0.1"
              (fn []
                (let [addr (-> srv (.address))
                      port (-> addr .-port)
                      host (-> addr .-address)]
                  (println (str "Socket REPL listening on port "
                                port " on host " host))))))))

(defn repl
  ([] (repl nil))
  ([_opts]
   (reset! last-ns 'user)
   (when tty (.setRawMode js/process.stdin true))
   (.then (js/Promise.resolve (js/eval "globalThis.user = globalThis.user || {};"))
          (fn [_]
            (js/Promise. (fn [resolve]
                           (input-loop nil resolve)))))))
