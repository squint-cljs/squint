(ns squint.repl.node
  (:require
   ["fs" :as fs]
   ["net" :as net]
   ["path" :as path]
   ["process" :as process]
   ["readline" :as readline]
   ["squint-cljs/core.js" :as squint]
   ["url" :as url]
   ["vm" :as vm]
   [clojure.string :as str]
   [edamame.core :as e]
   [shadow.esm :as esm]
   [squint.compiler :as compiler]
   [squint.compiler-common :refer [*async* *cljs-ns* *repl*]]))

(def pending-input (atom ""))

(declare input-loop eval-next)

(def in-progress (atom false))

(defn continue [rl socket]
  (reset! in-progress false)
  (.setPrompt ^js rl (str *cljs-ns* "=> "))
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

(defn eval-expr [socket f]
  (let [ctx #js {:f f}
        _ (.createContext vm ctx)]
    (try
      (when (and tty (not socket))
        (.setRawMode js/process.stdin false))
      (-> (.runInContext vm "f()" ctx
                         #js {:displayErrors true
                              ;; :timeout 1000
                              :breakOnSigint true
                              :microtaskMode "afterEvaluate"})
          (.then (fn [wrapper]
                   (let [ctx #js {:f (if socket (fn [] wrapper)
                                         (fn []
                                           (let [v (first wrapper)]
                                             (squint/prn v)
                                             wrapper)))}
                         _ (.createContext vm ctx)]
                     (.runInContext vm "f()" ctx
                                    #js {:displayErrors true
                                         ;; :timeout 1000
                                         :breakOnSigint true
                                         :microtaskMode "afterEvaluate"}))))
          (.finally (fn []
                      (when (and tty (not socket))
                        (.setRawMode js/process.stdin true)))))
      (catch :default e
        (when (and tty (not socket))
          (.setRawMode js/process.stdin true))
        (js/Promise.reject e)))))

(def last-ns (atom *cljs-ns*))

#_(defn eval-js [js-str]
  (let [filename (str ".repl/" (gensym) ".mjs")]
    (when-not (fs/existsSync ".repl")
      (fs/mkdirSync ".repl"))
    (fs/writeFileSync filename js-str)
    (-> (esm/dynamic-import (-> (path/resolve (process/cwd) filename)
                                url/pathToFileURL
                                str))
        (.finally (fn [] #_(prn filename) (fs/unlinkSync filename))))))

(defn compile [the-val rl socket]
  (let [{js-str :javascript
         cljs-ns :ns} (binding [*cljs-ns* @last-ns]
                        (compiler/compile-string* (binding [*print-meta* true]
                                                    (pr-str the-val)) {:context :return
                                                                       :elide-exports true}))
        js-str (str/replace "(async function () {\n%s\n}) ()" "%s" js-str)]
    #_(println :js-str js-str)
    (reset! last-ns cljs-ns)
    (->
     (js/Promise.resolve (js/eval js-str))
     (.then (fn [^js val]
              (squint/println val)
              (eval-next socket rl)))
     (.catch (fn [err]
               (squint/println err)
               (continue rl socket))))))

(defn eval-next [socket rl]
  (when (or @in-progress (not (str/blank? @pending-input)))
    (reset! in-progress true)
    (let [rdr (e/reader @pending-input)
          the-val (try (e/parse-next rdr)
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
                  ;; (prn :pending @pending)
                  (compile the-val rl socket)
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
    (.setPrompt rl (str *cljs-ns* "=> "))
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
