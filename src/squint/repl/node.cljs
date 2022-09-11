(ns squint.repl.node
  (:require
   ["net" :as net]
   ["readline" :as readline]
   ["vm" :as vm]
   [clojure.string :as str]
   [edamame.core :as e]
   [squint.compiler :as compiler]))

(def last-ns (atom *ns*))

(def pending-input (atom ""))

(declare input-loop eval-next)

(def in-progress (atom false))

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

(def contextify-binding (js/process.binding "contextify"))

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
                                             (prn v)
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

(defn eval-next [socket rl]
  (when-not (or @in-progress (str/blank? @pending-input))
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
                  (do (prn (js/eval (:javascript
                                     (doto (compiler/compile-string* (pr-str the-val)
                                                                     {:elide-exports true})
                                       #_prn))))
                      (continue rl socket))
                  #_(-> (eval-expr
                         socket
                         #(eval-next nil nil
                                     {:wrap vector
                                      ;; TODO this is a huge workaround
                                      ;; we should instead re-organize the code in nbb.core
                                      :parse-fn (let [realized? (atom false)]
                                                  (fn [_]
                                                    (if-not @realized?
                                                      (do
                                                        (reset! realized? true)
                                                        the-val)
                                                      :sci.core/eof)))}))
                        (.then (fn [v]
                                 (let [[val ns]
                                       [(first v) (eval-form '*ns*)]]
                                   (reset! last-ns ns)
                                   (sci/alter-var-root sci/*3 (constantly @sci/*2))
                                   (sci/alter-var-root sci/*2 (constantly @sci/*1))
                                   (sci/alter-var-root sci/*1 (constantly val))
                                   (when socket
                                     (.write socket (prn-str val))
                                     #_(prn val))
                                   (continue rl socket))))
                        (.catch (fn [err]
                                  (prn (str err))
                                  (sci/alter-var-root sci/*e (constantly err))
                                  (continue rl socket))))
                  (reset! in-progress false)))))))

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
             (create-rl))
        _ (on-line rl socket)
        _ (.setPrompt rl (str @last-ns "=> "))
        _ (.on rl "close" resolve)]
    (prn :yo)
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
   (when tty (.setRawMode js/process.stdin true))
   (js/Promise. (fn [resolve]
                  (input-loop nil resolve)))))

