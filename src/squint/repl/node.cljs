(ns squint.repl.node
  (:require
   ["net" :as net]
   ["readline" :as readline]
   ["squint-cljs/core.js" :as squint]
   ["vm" :as vm]
   ["node:util" :as util]
   [clojure.string :as str]
   [edamame.core :as e]
   [squint.compiler :as compiler]
   [squint.compiler-common :refer [*async* *cljs-ns* *repl*]]))

(def pending-input (atom ""))

(declare input-loop eval-next)

(def in-progress (atom false))

(def last-ns (atom *cljs-ns*))

(defn continue [rl socket]
  (reset! in-progress false)
  (.setPrompt ^js rl (str @last-ns "=> "))
  (.prompt rl)
  (when-not (str/blank? @pending-input)
    (eval-next socket rl)))

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
         :as new-state} (binding [*cljs-ns* @last-ns]
                        (compiler/compile-string* (binding [*print-meta* true]
                                                    (pr-str the-val)) {:context :return
                                                                       :elide-exports true}
                                                  @state))
        _ (reset! state new-state)
        js-str (str/replace "(async function () {\n%s\n}) ()" "%s" js-str)]
    (reset! last-ns cljs-ns)
    (->
     (js/Promise.resolve (js/eval js-str))
     (.then (fn [^js val]
              (if socket
                (.write socket (util/inspect val) "\n")
                (js/console.log val))
              (eval-next socket rl)))
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
                  (do (*print-err-fn* "the-val" (pr-str the-val))
                      (compile the-val rl socket))
                  (continue rl socket) #_(reset! in-progress false)))))))

(defn input-handler [socket rl input]
  (swap! pending-input str input "\n")
  (eval-next socket rl))

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
    (.on rl "close" resolve)
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
   (set! *cljs-ns* 'user)
   (set! *repl* true)
   (set! *async* true)
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
   (set! *cljs-ns* 'user)
   (set! *repl* true)
   (set! *async* true)
   (when tty (.setRawMode js/process.stdin true))
   (.then (js/Promise.resolve (js/eval "globalThis.user = globalThis.user || {};"))
          (fn [_]
            (js/Promise. (fn [resolve]
                           (input-loop nil resolve)))))))
