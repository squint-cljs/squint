(ns squint.compiler.js-ast
  "ESTree-shaped JS AST nodes, and a printer that emits source maps.
  A node is a map with a kebab-case :type keyword and kebab-case ESTree fields.
  Namespaced keys are printer hints outside ESTree.
  A :raw node holds emitted text and nested nodes."
  (:require [clojure.string :as str]))

(declare print-js)

(defrecord Node [type]
  Object
  (toString [this] (print-js this)))

(defn node
  "Returns a node of type with the fields in kvs."
  ([type] (map->Node {:type type}))
  ([type k1 v1] (map->Node {:type type k1 v1}))
  ([type k1 v1 k2 v2] (map->Node {:type type k1 v1 k2 v2}))
  ([type k1 v1 k2 v2 k3 v3] (map->Node {:type type k1 v1 k2 v2 k3 v3}))
  ([type k1 v1 k2 v2 k3 v3 k4 v4]
   (map->Node {:type type k1 v1 k2 v2 k3 v3 k4 v4}))
  ([type k1 v1 k2 v2 k3 v3 k4 v4 k5 v5]
   (map->Node {:type type k1 v1 k2 v2 k3 v3 k4 v4 k5 v5}))
  ([type k1 v1 k2 v2 k3 v3 k4 v4 k5 v5 & kvs]
   (map->Node (apply array-map :type type k1 v1 k2 v2 k3 v3 k4 v4 k5 v5 kvs))))

(defn node? [x]
  (instance? Node x))

(defn code?
  "Returns true if x is a Code record."
  [x]
  (and (map? x) (contains? x :js) (not (node? x))))

(defn structured?
  "Returns true if x prints through the printer rather than as a plain string."
  [x]
  (or (node? x)
      (and (code? x) (structured? (:js x)))
      (and (sequential? x) (some structured? x))))

(defn raw
  "Returns emitted text built from parts, or a :raw node if a part is a node.
  A part is a string, node, Code record, nil or a sequence of those."
  [& parts]
  (if (some structured? parts)
    (node :raw :parts (vec parts))
    (print-js parts)))

(defn terminate
  "Returns a :raw node that prints x followed by ;\\n, unless the output
  already ends with ;\\n."
  [x]
  (node :raw :parts [x] :squint/terminate true))

(declare walk)

(defn- walk-each [xs sep out enter exit]
  (reduce (fn [first? x]
            (when-not first? ((:emit out) sep))
            (walk x out enter exit)
            false)
          true xs))

(defn- walk-function [{:keys [id params body async generator expression]
                       :as n} out enter exit]
  (let [f (:emit out)
        arrow? (= :arrow-function-expression (:type n))
        w #(walk % out enter exit)
        params! #(do (f "(") (walk-each params ", " out enter exit) (f ")"))
        stmts (:body body)
        body! #(if expression (w body) (do (f " {\n") (w stmts) (f "\n}")))]
    (when async (f "async "))
    (cond
      (:squint/iife n)
      (do (f (if generator "function* () {\n" "() => {\n")) (w stmts) (f "\n}"))
      id
      (do (f "function") (when generator (f "*")) (f " ") (w id) (f " ")
          (params!) (when arrow? (f "=>")) (body!))
      :else
      (do (when-not arrow? (f "function")) (when generator (f "*"))
          (when (or (not arrow?) async) (f " "))
          (params!) (when arrow? (f "=>")) (body!)))))

(defn- walk-node [n out enter exit]
  (let [f (:emit out)
        w #(walk % out enter exit)
        each #(walk-each % ", " out enter exit)]
    (case (:type n)
      :raw (do (w (:parts n))
               (when (and (:squint/terminate n) (not= ";\n" @(:tail out)))
                 (f ";\n")))
      :program (w (:body n))
      :expression-statement (do (w (:expression n)) (f ";\n"))
      :return-statement (do (f "return ") (let [a (:argument n)] (if (nil? a) (f "null") (w a))))
      :array-expression (do (f "[") (each (:elements n)) (f "]"))
      :call-expression (let [callee (:callee n)]
                         (if (:squint/iife callee)
                           (do (f "(") (w callee) (f ")()"))
                           (do (w callee) (f "(") (each (:arguments n)) (f ")"))))
      :new-expression (do (f "new ") (w (:callee n)) (f "(") (each (:arguments n)) (f ")"))
      :member-expression (do (w (:object n)) (f ".") (w (:property n)))
      :parenthesized-expression (do (f "(") (w (:expression n)) (f ")"))
      :await-expression (do (f "(await ") (w (:argument n)) (f ")"))
      :yield-expression (do (f (if (:delegate n) "(yield* " "(yield ")) (w (:argument n)) (f ")"))
      :conditional-expression (do (f "((") (w (:test n)) (f ") ? (") (w (:consequent n))
                                  (f ") : (") (w (:alternate n)) (f "))"))
      :block-statement (do (f "{\n") (w (:body n)) (f "}"))
      :if-statement (do (f "if (") (w (:test n)) (f ") ") (w (:consequent n))
                        (when-let [alt (:alternate n)] (f " else ") (w alt)))
      :variable-declaration (do (w (:kind n)) (f " ") (each (:declarations n)) (f ";\n"))
      :variable-declarator (do (w (:id n)) (f " = ") (w (:init n)))
      :assignment-expression (do (w (:left n)) (f " ") (f (:operator n)) (f " ") (w (:right n)))
      (:function-expression :arrow-function-expression) (walk-function n out enter exit))))

(defn- walk
  "Emits each text chunk of x in print order through out. Calls (enter loc)
  and (exit) around a node with a :loc, if enter is given."
  [x out enter exit]
  (cond
    (string? x) ((:emit out) x)
    (nil? x) nil
    (node? x) (let [loc (when enter (:loc x))]
                (when loc (enter loc))
                (walk-node x out enter exit)
                (when loc (exit)))
    (code? x) (let [loc (when enter (:loc x))]
                (when loc (enter loc))
                (walk (:js x) out enter exit)
                (when loc (exit)))
    (sequential? x) (reduce (fn [_ y] (walk y out enter exit)) nil x)
    :else ((:emit out) (str x))))

(defn- output
  "Returns an output that passes each chunk to f and tracks the last two
  characters written."
  [f]
  (let [tail (volatile! "")]
    {:tail tail
     :emit (fn [^String s]
             (let [c (count s)]
               (when (pos? c)
                 (vreset! tail (if (>= c 2)
                                 (subs s (- c 2))
                                 (let [t (str @tail s)
                                       ct (count t)]
                                   (if (> ct 2) (subs t (- ct 2)) t))))
                 (f s))))}))

(defn print-js
  "Returns the JS text of x, a string, node, Code record or vector of those."
  [x]
  (if (string? x)
    x
    #?(:clj (let [sb (StringBuilder.)]
              (walk x (output #(.append sb ^String %)) nil nil)
              (.toString sb))
       :cljs (let [arr #js []]
               (walk x (output #(.push arr %)) nil nil)
               (.join arr "")))))

(def ^:private enough #?(:clj (Exception. "enough") :cljs (js/Error. "enough")))

(defn head
  "Returns the first n characters of the text of x."
  [n x]
  (if (string? x)
    (if (< (count x) n) x (subs x 0 n))
    (let [acc (volatile! "")]
      (try
        (walk x (output (fn [s]
                          (vswap! acc str s)
                          (when (>= (count @acc) n) (throw enough))))
              nil nil)
        (catch #?(:clj Exception :cljs :default) e
          (when-not (identical? e enough) (throw e))))
      (let [a @acc] (if (< (count a) n) a (subs a 0 n))))))

(defn empty-text?
  "Returns true if x prints as an empty string."
  [x]
  (cond (nil? x) true
        (string? x) (= "" x)
        (code? x) (empty-text? (:js x))
        :else false))

(defn with-loc
  "Returns x with the source position of form, if form has one and x prints
  as text. Puts the position of a return statement on its argument.
  Wraps a string in a :raw node."
  [x form]
  (let [{:keys [line column]} (meta form)]
    (if (and line column)
      (let [loc {:start {:line line :column (dec column)}}
            attach (fn attach [x]
                     (cond (:loc x) x
                           (= :return-statement (:type x)) (update x :argument attach)
                           (node? x) (assoc x :loc loc)
                           (code? x) (update x :js attach)
                           (and (string? x) (not (str/blank? x))) (node :raw :parts [x] :loc loc)
                           :else x))]
        (attach x))
      x)))

(def ^:private base64-chars
  "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/")

(defn vlq
  "Returns the base64 VLQ encoding of integer n."
  [n]
  (loop [v (if (neg? n) (inc (* 2 (- n))) (* 2 n))
         acc ""]
    (let [digit (bit-and v 31)
          v (unsigned-bit-shift-right v 5)
          digit (if (pos? v) (bit-or digit 32) digit)
          acc (str acc (nth base64-chars digit))]
      (if (pos? v) (recur v acc) acc))))

(defn- encode-mappings
  "Returns the mappings string for segments [gen-line gen-col src-line src-col],
  0-based and sorted."
  [segments]
  (loop [segments segments
         line 0
         prev-gen-col 0 prev-src-line 0 prev-src-col 0
         out [] first-in-line true]
    (if-let [[gl gc sl sc] (first segments)]
      (if (> gl line)
        (recur segments (inc line) 0 prev-src-line prev-src-col (conj out ";") true)
        (recur (rest segments) line gc sl sc
               (conj out (str (when-not first-in-line ",")
                              (vlq (- gc prev-gen-col)) "A"
                              (vlq (- sl prev-src-line))
                              (vlq (- sc prev-src-col))))
               false))
      (apply str out))))

(defn- count-newlines [^String s]
  (loop [i 0 n 0]
    (let [j (str/index-of s "\n" i)]
      (if j (recur (inc j) (inc n)) n))))

(defn print-with-map
  "Returns {:js text :segments [[gen-line gen-col src-line src-col] ..]} for x.
  Lines and columns are 0-based."
  [x]
  (let [#?@(:clj [sb (StringBuilder.)] :cljs [arr #js []])
        line (volatile! 0)
        col (volatile! 0)
        segments (volatile! (transient []))
        stack (volatile! ())
        mark! (fn [{{l :line c :column} :start}]
                (let [segs @segments
                      n (count segs)
                      seg [@line @col (dec l) c]
                      prev (when (pos? n) (nth segs (dec n)))]
                  (if (and prev (= (nth prev 0) @line) (= (nth prev 1) @col))
                    (vreset! segments (assoc! segs (dec n) seg))
                    (vreset! segments (conj! segs seg)))))
        f (fn [^String s]
            #?(:clj (.append sb s) :cljs (.push arr s))
            (let [idx (str/last-index-of s "\n")]
              (if idx
                (do (vswap! line + (count-newlines s))
                    (vreset! col (- (count s) (inc idx))))
                (vswap! col + (count s)))))
        enter (fn [loc]
                (mark! loc)
                (vswap! stack conj loc))
        exit (fn []
               (vswap! stack rest)
               (when-let [parent (first @stack)]
                 (mark! parent)))]
    (walk x (output f) enter exit)
    {:js #?(:clj (.toString sb) :cljs (.join arr ""))
     :segments (persistent! @segments)}))

(defn- json-escape [c]
  (case c
    \" "\\\""
    \\ "\\\\"
    \newline "\\n"
    \return "\\r"
    \tab "\\t"
    (let [i #?(:clj (int c) :cljs (.charCodeAt c 0))]
      (when (< i 32)
        (let [h #?(:clj (Integer/toHexString i) :cljs (.toString i 16))]
          (str "\\u" (subs "0000" (count h)) h))))))

(defn- json-str [s]
  (str "\"" (str/escape s json-escape) "\""))

(defn source-map
  "Returns a source map v3 JSON string for segments, mapping file to source.
  source-content is the text of source, or nil if absent."
  [segments {:keys [file source source-content]}]
  (str "{\"version\":3"
       (when file (str ",\"file\":" (json-str file)))
       ",\"sources\":[" (json-str (or source "")) "]"
       (when source-content
         (str ",\"sourcesContent\":[" (json-str source-content) "]"))
       ",\"names\":[]"
       ",\"mappings\":" (json-str (encode-mappings segments))
       "}"))

(defn shift-segments
  "Returns segments moved down by the lines of prefix text."
  [segments prefix]
  (let [lines (count-newlines prefix)
        idx (str/last-index-of prefix "\n")
        col-shift (if idx (- (count prefix) (inc idx)) (count prefix))]
    (mapv (fn [[gl gc sl sc]]
            [(+ gl lines) (if (zero? gl) (+ gc col-shift) gc) sl sc])
          segments)))
