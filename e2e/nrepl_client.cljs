(ns nrepl-client
  "Minimal nREPL client over bencode TCP, written in squint. Shared by the e2e
  tests (browser-repl + node nREPL server) so the bencode + transport code lives
  in one place."
  (:require ["node:net" :as net]
            [clojure.string :as str]))

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

(defn msg-field
  "First non-nil value of string key `k` across the response messages."
  [msgs k]
  (some (fn [m] (aget m k)) (js/Array.from msgs)))

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

(defn nrepl-eval
  ([client session code] (nrepl-eval client session code nil))
  ([client session code ns]
   (-> (nrepl-request client (cond-> #js {:op "eval" :session session :code code}
                               ns (doto (aset "ns" ns))))
       (.then (fn [msgs]
                (some (fn [m] (aget m "value")) (js/Array.from msgs)))))))
