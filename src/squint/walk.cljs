(ns clojure.walk
  "Port of clojure.walk to squint's built-in, mutable data structures.

  Traverses arbitrary nested data, calling `inner` on each element and `outer`
  on each (sub)collection. Collections are rebuilt with `into`/`empty`, which
  carry metadata, so metadata is preserved like in Clojure.

  Note: in squint keywords are strings, so `keywordize-keys` and
  `stringify-keys` both normalize map keys to strings via `name`; they are
  provided for API compatibility with clojure.walk.")

(defn walk
  "Traverses form, an arbitrary data structure. inner and outer are functions.
  Applies inner to each element of form, building up a data structure of the
  same type, then applies outer to the result. Recognizes maps, vectors, sets,
  lists and other seqs.

  Metadata is preserved: the map/vector/set branches rely on squint's `empty`
  and `into` carrying metadata (like Clojure), while the list and seq branches
  reattach it explicitly via `with-meta`."
  [inner outer form]
  (cond
    (list? form) (outer (with-meta (apply list (map inner form)) (meta form)))
    (map? form) (outer (into (empty form) (map inner form)))
    (vector? form) (outer (into (empty form) (map inner form)))
    (set? form) (outer (into (empty form) (map inner form)))
    ;; strings are seqable in squint (and keywords are strings), so treat them
    ;; as atoms rather than recurring into their characters
    (and (seq? form) (not (string? form))) (outer (with-meta (vec (map inner form)) (meta form)))
    :else (outer form)))

(defn postwalk
  "Performs a depth-first, post-order traversal of form. Calls f on each
  sub-form, uses f's return value in place of the original."
  [f form]
  (walk (fn [x] (postwalk f x)) f form))

(defn prewalk
  "Like postwalk, but does pre-order traversal."
  [f form]
  (walk (fn [x] (prewalk f x)) identity (f form)))

(defn postwalk-replace
  "Recursively transforms form by replacing keys in smap with their values.
  Does replacement at the leaves of the tree first."
  [smap form]
  (postwalk (fn [x] (if (contains? smap x) (get smap x) x)) form))

(defn prewalk-replace
  "Recursively transforms form by replacing keys in smap with their values.
  Does replacement at the root of the tree first."
  [smap form]
  (prewalk (fn [x] (if (contains? smap x) (get smap x) x)) form))

(defn postwalk-demo
  "Demonstrates the behavior of postwalk by printing each form as it is walked."
  [form]
  (postwalk (fn [x] (println "Walked:" (pr-str x)) x) form))

(defn prewalk-demo
  "Demonstrates the behavior of prewalk by printing each form as it is walked."
  [form]
  (prewalk (fn [x] (println "Walked:" (pr-str x)) x) form))

(defn keywordize-keys
  "Recursively normalizes all map keys to strings via `name`. In Clojure this
  turns string keys into keywords, but in squint keywords are strings, so keys
  are simply normalized to strings."
  [m]
  (postwalk
   (fn [x]
     (if (map? x)
       (into (empty x) (map (fn [[k v]] [(name k) v]) x))
       x))
   m))

(defn stringify-keys
  "Recursively transforms all map keys to strings via `name`."
  [m]
  (postwalk
   (fn [x]
     (if (map? x)
       (into (empty x) (map (fn [[k v]] [(name k) v]) x))
       x))
   m))
