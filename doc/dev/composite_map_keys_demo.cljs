;; composite-map-keys prototype, squint defclass. self-contained, no squint
;; internals. lazyseq aware. API mirrors a map: get/assoc/contains/dissoc/keys.

(defn enc-into [x ^js out]
  (cond
    (nil? x)     (.push out "_")
    (number? x)  (.push out "i" x "e")
    (boolean? x) (.push out (if x "t" "f"))
    (string? x)  (.push out (.-length x) ":" x)
    (array? x)   (do (.push out "l")
                     (dotimes [i (.-length x)] (enc-into (aget x i) out))
                     (.push out "e"))
    ;; any other iterable (lazy seq, list, range) -> same form as a vector
    (and (fn? (aget x js/Symbol.iterator))
         (not (instance? js/Map x))
         (not (instance? js/Set x)))
    (do (.push out "l") (doseq [e x] (enc-into e out)) (.push out "e"))
    (instance? js/Set x)
    (let [parts (array)]
      (doseq [e x] (let [o (array)] (enc-into e o) (.push parts (.join o ""))))
      (.sort parts)
      (.push out "s") (doseq [p parts] (.push out p)) (.push out "e"))
    (instance? js/Map x)
    (let [parts (array)]
      (doseq [e x]
        (let [o (array)] (enc-into (aget e 0) o) (enc-into (aget e 1) o)
          (.push parts (.join o ""))))
      (.sort parts)
      (.push out "d") (doseq [p parts] (.push out p)) (.push out "e"))
    :else
    (let [ks (.sort (js/Object.keys x))]
      (.push out "d")
      (doseq [k ks] (.push out (.-length k) ":" k) (enc-into (aget x k) out))
      (.push out "e"))))

(defn enc-key [x]
  (let [out (array)] (enc-into x out) (.join out "")))

(defclass EncMap
  (field -m)
  (constructor [this] (set! -m (js/Map.)))

  Object
  ;; key ops run enc-key; stored value is #js [origKey v] so keys roundtrip
  (assoc [this k v] (.set -m (enc-key k) #js [k v]) this)
  (lookup [this k] (let [e (.get -m (enc-key k))] (when (some? e) (aget e 1))))
  (contains [this k] (.has -m (enc-key k)))
  (dissoc [this k] (.delete -m (enc-key k)) this)
  (count [this] (.-size -m))
  (keys [this] (map #(aget % 0) (.values -m)))
  (vals [this] (map #(aget % 1) (.values -m)))
  (entries [this] (map #(vector (aget % 0) (aget % 1)) (.values -m))))

;; defclass cannot express a [Symbol.iterator] method (munges the name), so
;; attach it on the prototype to make instances seqable.
(set! (aget (.-prototype EncMap) js/Symbol.iterator)
      (fn []
        (this-as this
          (let [it (.entries this)]            ;; entries is a seqable lazy seq
            (.call (aget it js/Symbol.iterator) it)))))

;; sanity
(assert (= (enc-key [1 2]) (enc-key (map inc [0 1]))) "vector == lazyseq")
(assert (= (enc-key [1 2]) (enc-key (range 1 3)))      "vector == range")
(assert (not= (enc-key [1 2]) (enc-key [2 1])))
(assert (not= (enc-key ["12"]) (enc-key [1 2])))
(assert (= (enc-key {:a 1 :b 2}) (enc-key {:b 2 :a 1})))
(println "encoder sanity ok")

(let [m (EncMap.)]
  (.assoc m [1 2] :a)
  (.assoc m [3 4] :b)
  (println "lookup [1 2]        ->" (.lookup m [1 2]))
  (println "lookup (lazyseq 1 2)->" (.lookup m (map inc [0 1])))  ;; hits vector entry
  (println "contains [3 4]      ->" (.contains m [3 4]))
  (println "lookup [9 9]        ->" (.lookup m [9 9]))
  (println "count               ->" (.count m))
  (println "keys                ->" (vec (.keys m)))
  (println "entries             ->" (vec (.entries m)))
  (println "seq (for over inst) ->" (vec (for [e m] e))))  ;; Symbol.iterator
