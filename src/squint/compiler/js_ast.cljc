(ns squint.compiler.js-ast
  "JS AST nodes isomorphic to ESTree, and a printer that emits source maps.
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

(defn- code? [x]
  (and (map? x) (contains? x :js) (not (node? x))))

(defn- structured?
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

(defn- interpose-comma [xs]
  (vec (interpose ", " xs)))

(defn- function-parts [{:keys [id params body async generator expression]
                         :as n}]
  (let [arrow? (= :arrow-function-expression (:type n))
        params ["(" (interpose-comma params) ")"]
        stmts (:body body)
        body (if expression body [" {\n" stmts "\n}"])]
    (cond
      (:squint/iife n)
      (if generator
        [(when async "async ") "function* () {\n" stmts "\n}"]
        [(when async "async ") "() => {\n" stmts "\n}"])
      id
      [(when async "async ") "function" (when generator "*") " " id " "
       params (when arrow? "=>") body]
      :else
      [(when async "async ") (when-not arrow? "function") (when generator "*")
       (when (or (not arrow?) async) " ")
       params (when arrow? "=>") body])))

(defn- parts
  "Returns the children and text of node n in print order."
  [n]
  (case (:type n)
    :raw (:parts n)
    :program (:body n)
    :expression-statement [(:expression n) ";\n"]
    :return-statement ["return " (let [a (:argument n)] (if (nil? a) "null" a))]
    :array-expression ["[" (interpose-comma (:elements n)) "]"]
    :call-expression (let [callee (:callee n)]
                       (if (:squint/iife callee)
                         ["(" callee ")()"]
                         [callee "(" (interpose-comma (:arguments n)) ")"]))
    :new-expression ["new " (:callee n) "(" (interpose-comma (:arguments n)) ")"]
    :member-expression [(:object n) "." (:property n)]
    :parenthesized-expression ["(" (:expression n) ")"]
    :await-expression ["(await " (:argument n) ")"]
    :yield-expression ["(yield* " (:argument n) ")"]
    :conditional-expression ["((" (:test n) ") ? (" (:consequent n) ") : ("
                             (:alternate n) "))"]
    :block-statement ["{\n" (:body n) "}"]
    :if-statement (let [alt (:alternate n)]
                    ["if (" (:test n) ") " (:consequent n)
                     (when alt [" else " alt])])
    :variable-declaration [(:kind n) " " (interpose-comma (:declarations n)) ";\n"]
    :variable-declarator [(:id n) " = " (:init n)]
    (:function-expression :arrow-function-expression) (function-parts n)))

(declare walk)

(defn- walk-each [xs sep f enter exit]
  (reduce (fn [first? x]
            (when-not first? (f sep))
            (walk x f enter exit)
            false)
          true xs))

(defn- walk-function [{:keys [id params body async generator expression]
                       :as n} f enter exit]
  (let [arrow? (= :arrow-function-expression (:type n))
        w #(walk % f enter exit)
        params! #(do (f "(") (walk-each params ", " f enter exit) (f ")"))
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

(defn- walk-node [n f enter exit]
  (let [w #(walk % f enter exit)
        each #(walk-each % ", " f enter exit)]
    (case (:type n)
      :raw (w (:parts n))
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
      :yield-expression (do (f "(yield* ") (w (:argument n)) (f ")"))
      :conditional-expression (do (f "((") (w (:test n)) (f ") ? (") (w (:consequent n))
                                  (f ") : (") (w (:alternate n)) (f "))"))
      :block-statement (do (f "{\n") (w (:body n)) (f "}"))
      :if-statement (do (f "if (") (w (:test n)) (f ") ") (w (:consequent n))
                        (when-let [alt (:alternate n)] (f " else ") (w alt)))
      :variable-declaration (do (w (:kind n)) (f " ") (each (:declarations n)) (f ";\n"))
      :variable-declarator (do (w (:id n)) (f " = ") (w (:init n)))
      (:function-expression :arrow-function-expression) (walk-function n f enter exit))))

(defn- walk
  "Calls (f s) for each text chunk of x in print order. Calls (enter loc)
  and (exit) around a node with a :loc, if enter is given."
  [x f enter exit]
  (cond
    (string? x) (f x)
    (nil? x) nil
    (node? x) (let [loc (when enter (:loc x))]
                (when loc (enter loc))
                (walk-node x f enter exit)
                (when loc (exit)))
    (code? x) (let [loc (when enter (:loc x))]
                (when loc (enter loc))
                (walk (:js x) f enter exit)
                (when loc (exit)))
    (sequential? x) (reduce (fn [_ y] (walk y f enter exit)) nil x)
    :else (f (str x))))

(defn print-js
  "Returns the JS text of x, a string, node, Code record or vector of those."
  [x]
  (if (string? x)
    x
    #?(:clj (let [sb (StringBuilder.)]
              (walk x #(.append sb ^String %) nil nil)
              (.toString sb))
       :cljs (let [arr #js []]
               (walk x #(.push arr %) nil nil)
               (.join arr "")))))

(defn- children-rev
  "Returns the parts of x in reverse print order."
  [x]
  (cond (node? x) (rseq (vec (parts x)))
        (code? x) [(:js x)]
        (sequential? x) (rseq (vec x))))

(defn tail
  "Returns the last n characters of the text of x."
  [n x]
  (if (string? x)
    (let [c (count x)] (if (< c n) x (subs x (- c n))))
    (loop [stack (list x) acc ""]
      (if (or (>= (count acc) n) (empty? stack))
        (let [c (count acc)] (if (< c n) acc (subs acc (- c n))))
        (let [[y & more] stack]
          (cond (nil? y) (recur more acc)
                (string? y) (recur more (str y acc))
                (or (node? y) (code? y) (sequential? y))
                (recur (concat (children-rev y) more) acc)
                :else (recur more (str y acc))))))))

(defn- children
  [x]
  (cond (node? x) (parts x)
        (code? x) [(:js x)]
        (sequential? x) x))

(defn head
  "Returns the first n characters of the text of x."
  [n x]
  (if (string? x)
    (if (< (count x) n) x (subs x 0 n))
    (loop [stack (list x) acc ""]
      (if (or (>= (count acc) n) (empty? stack))
        (if (< (count acc) n) acc (subs acc 0 n))
        (let [[y & more] stack]
          (cond (nil? y) (recur more acc)
                (string? y) (recur more (str acc y))
                (or (node? y) (code? y) (sequential? y))
                (recur (concat (children y) more) acc)
                :else (recur more (str acc y))))))))

(defn blank?
  "Returns true if the text of x is empty or whitespace."
  [x]
  (if (string? x)
    (str/blank? x)
    (loop [stack (list x)]
      (if-let [[y & more] (seq stack)]
        (cond (nil? y) (recur more)
              (string? y) (if (str/blank? y) (recur more) false)
              (or (node? y) (code? y) (sequential? y)) (recur (concat (children y) more))
              :else (if (str/blank? (str y)) (recur more) false))
        true))))

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
    (walk x f enter exit)
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

(defn- camel [k]
  (let [[h & t] (str/split (name k) #"-")]
    (apply str h (map str/capitalize t))))

(defn ->estree
  "Returns node x as ESTree data with string type names and camelCase keys.
  Drops namespaced printer hints."
  [x]
  (cond
    (node? x) (reduce-kv (fn [m k v]
                           (cond (namespace k) m
                                 (= :type k) (let [s (camel v)]
                                               (assoc m "type" (str (str/upper-case (subs s 0 1))
                                                                    (subs s 1))))
                                 :else (assoc m (camel k) (->estree v))))
                         {} x)
    (code? x) (->estree (:js x))
    (map? x) (reduce-kv (fn [m k v] (assoc m (camel k) (->estree v))) {} x)
    (sequential? x) (mapv ->estree x)
    :else x))
