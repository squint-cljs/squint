;; hamt map demo. Run with: node node_cli.js run doc/dev/hamt_demo.cljs
;; An optional persistent hash map (HAMT) with value-semantic keys, living in
;; squint-cljs/src/squint/hamt.js. Core fns dispatch to it through the
;; existing protocol slots; nothing here needs new compiler support.
(ns hamt-demo
  (:require ["squint-cljs/src/squint/hamt.js" :as hamt :refer [IHash IHash__hash]]))

(println "-- basics --")
(def m (hamt/hash-map :a 1 :b 2))
(println "map:            " m)
(println "get:            " (get m :a))
(println "assoc:          " (assoc m :c 3))
(println "original intact:" m)
(println "dissoc:         " (dissoc m :a))
(println "count:          " (count m))
(println "map?:           " (map? m))

(println)
(println "-- composite keys (impossible with plain squint maps) --")
(def routes (-> (hamt/hash-map)
                (assoc [:get "/users"] :list-users)
                (assoc [:post "/users"] :create-user)))
(println "vector key:      " (get routes [:get "/users"]))
(println "seq key hits it: " (get routes (cons :get (list "/users"))))
(def by-point (assoc (hamt/hash-map) {:x 1 :y 2} "treasure"))
(println "map key:         " (get by-point {:y 2 :x 1}))

(println)
(println "-- value equality, both directions --")
(println "hamt = hamt:" (= (hamt/hash-map :a 1) (hamt/hash-map :a 1)))
(println "hamt = obj: " (= (hamt/hash-map :a 1) {:a 1}))
(println "obj = hamt: " (= {:a 1} (hamt/hash-map :a 1)))

(println)
(println "-- core fns dispatch through protocol slots --")
(println "into:     " (into (hamt/hash-map) [[:a 1] [:b 2]]))
(println "merge:    " (merge (hamt/hash-map :a 1) {:b 2}))
(println "seq:      " (seq (hamt/hash-map :a 1)))
(println "reduce-kv:" (reduce-kv (fn [acc _k v] (+ acc v)) 0 m))
(println "keys:     " (keys m))
(println "for:      " (pr-str (vec (for [[k v] m] [k v]))))

(println)
(println "-- hashing --")
(println "hash [1 2 3] = hash (list 1 2 3):" (= (hamt/hash [1 2 3]) (hamt/hash (list 1 2 3))))
(println "collision pair Aa/BB:" (= (hamt/hash "Aa") (hamt/hash "BB")))
(println "both survive:        " (hamt/hash-map "Aa" 1 "BB" 2))
(println "satisfies? IHash:    " (satisfies? IHash m))

(println)
(println "-- IHash as extension point --")
;; a wrapper type that hashes as its wrapped value; = already compares
;; instances structurally, so equal wrappers land in one map slot
(deftype Wrapper [v])
(extend-type Wrapper
  IHash
  (-hash [w] (hamt/hash (.-v w))))
(def wm (-> (hamt/hash-map)
            (assoc (->Wrapper [1 2]) :first)
            (assoc (->Wrapper [1 2]) :overwrites)))
(println "one entry:" (count wm) "value:" (get wm (->Wrapper [1 2])))
