(ns core-vars)

(def js-map (clj->js {:foo :bar}))

(js/console.log js-map)

(def clj-map {:foo/bar (+ 1 2 3)})

(js/console.log (get clj-map :foo/bar)) ;; => 6

(js/console.log (str clj-map))

(def log js/console.log)

(log (first [1 2 3]))

(defn foo [x]
  (dissoc x :foo))

(log (str (foo {:foo 1 :bar 2})))

(prn (reverse (map (fn [x] (inc x)) [1 2 3])))

(derive :foo/bar :foo/baz)

(prn (isa? :foo/bar :foo/baz))
