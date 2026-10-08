(ns squint.compiler.source-map
  "Source map v3 encoding."
  (:require [clojure.string :as str]))

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
  "Returns the mappings string for sorted segments [gen-line gen-col src-line
  src-col source-index], 0-based. source-index is 0 if absent."
  [segments]
  (loop [segments segments
         line 0
         prev-gen-col 0 prev-src 0 prev-src-line 0 prev-src-col 0
         out [] first-in-line true]
    (if-let [[gl gc sl sc si] (first segments)]
      (if (> gl line)
        (recur segments (inc line) 0 prev-src prev-src-line prev-src-col (conj out ";") true)
        (let [si (or si 0)]
          (recur (rest segments) line gc si sl sc
                 (conj out (str (when-not first-in-line ",")
                                (vlq (- gc prev-gen-col))
                                (vlq (- si prev-src))
                                (vlq (- sl prev-src-line))
                                (vlq (- sc prev-src-col))))
                 false)))
      (apply str out))))

(defn count-newlines
  "Returns the number of newlines in s."
  [^String s]
  (loop [i 0 n 0]
    (let [j (str/index-of s "\n" i)]
      (if j (recur (inc j) (inc n)) n))))

;; A source map holds only strings, so escaping them avoids a JSON library on the JVM.
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

(defn encode
  "Returns a source map v3 JSON string for segments, mapping file to sources.
  sources is a vector of source paths and sources-content a vector of their
  texts, with nil for an absent text."
  [segments {:keys [file sources sources-content]}]
  (let [json-list (fn [xs] (str "[" (str/join "," (map #(if (nil? %) "null" (json-str %)) xs)) "]"))]
    (str "{\"version\":3"
         (when file (str ",\"file\":" (json-str file)))
         ",\"sources\":" (json-list sources)
         (when (some some? sources-content)
           (str ",\"sourcesContent\":" (json-list sources-content)))
         ",\"names\":[]"
         ",\"mappings\":" (json-str (encode-mappings segments))
         "}")))

(defn shift-segments
  "Returns segments moved down by the lines of prefix text."
  [segments prefix]
  (let [lines (count-newlines prefix)
        idx (str/last-index-of prefix "\n")
        col-shift (if idx (- (count prefix) (inc idx)) (count prefix))]
    (mapv (fn [[gl gc & more]]
            (into [(+ gl lines) (if (zero? gl) (+ gc col-shift) gc)] more))
          segments)))
