;; hamt map demo. Run with: node node_cli.js run doc/dev/hamt_demo.cljs
;; An optional persistent hash map (HAMT) with value-semantic keys, living in
;; squint.immutable. Core fns dispatch to it through the existing protocol
;; slots. hash and IHash are core vars.
(ns hamt-demo
  (:require [squint.immutable :as hamt]))

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
                (assoc (hamt/vector :get "/users") :list-users)
                (assoc (hamt/vector :post "/users") :create-user)))
(println "pvec key:        " (get routes (hamt/vector :get "/users")))
(def by-point (assoc (hamt/hash-map) (hamt/hash-map :x 1 :y 2) "treasure"))
(println "map key:         " (get by-point (hamt/hash-map :y 2 :x 1)))
;; plain data keys by reference and survives mutation
(def k (js/Array. 1 2))
(def refs (assoc (hamt/hash-map) k :ref))
(.push k 3)
(println "reference key:   " (get refs k))

(println)
(println "-- equiv equality --")
(println "hamt = hamt:  " (= (hamt/hash-map :a 1) (hamt/hash-map :a 1)))
(println "hamt != obj:  " (not= (hamt/hash-map :a 1) {:a 1}))
(println "same ref elem:" (let [o {:x 1}] (= (hamt/vector o) (hamt/vector o))))

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
(println "pvec hash by value:  " (= (hash (hamt/vector 1 2)) (hash (hamt/vector 1 2))))
(println "array hash by uid:   " (not= (hash [1 2]) (hash [1 2])))
(println "collision pair Aa/BB:" (= (hash "Aa") (hash "BB")))
(println "both survive:        " (hamt/hash-map "Aa" 1 "BB" 2))
(println "satisfies? IHash:    " (satisfies? IHash m))

(println)
(println "-- IEquiv + IHash as extension points --")
;; a value type implements both: equiv-equal wrappers land in one map slot
(deftype Wrapper [v])
(extend-type Wrapper
  IEquiv
  (-equiv [w other] (and (instance? Wrapper other) (= (.-v w) (.-v other))))
  IHash
  (-hash [w] (hash (.-v w))))
(def wm (-> (hamt/hash-map)
            (assoc (->Wrapper (hamt/vector 1 2)) :first)
            (assoc (->Wrapper (hamt/vector 1 2)) :overwrites)))
(println "one entry:" (count wm) "value:" (get wm (->Wrapper (hamt/vector 1 2))))

(println)
(println "-- persistent vector --")
(def pv (hamt/vector 1 2 3))
(println "vector:      " pv)
(println "conj:        " (conj pv 4))
(println "assoc:       " (assoc pv 0 :x))
(println "peek/pop:    " (peek pv) (pop pv))
(println "subvec:      " (subvec pv 1))
(println "= pvec:      " (= pv (hamt/vector 1 2 3)))
(println "as map key:  " (get (assoc (hamt/hash-map) (hamt/vector 1 2) :hit) (hamt/vector 1 2)))

(println)
(println "-- persistent set --")
(def ps (hamt/hash-set 1 2 3))
(println "set:          " ps)
(println "conj/disj:    " (conj ps 4) (disj ps 1))
(println "composite:    " (contains? (hamt/hash-set (hamt/vector 1 2)) (hamt/vector 1 2)))
(println "= pset:       " (= ps (hamt/hash-set 2 1 3)))
(println "set as key:   " (get (assoc (hamt/hash-map) (hamt/hash-set 1 2) :hit) (hamt/hash-set 2 1)))
