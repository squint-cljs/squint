(ns fn)

(defn foo [x y z]
  (let [x (+ x 10)]
    [x y z]))

(prn (foo 1 2 3))

(def x (assoc! {} :foo 1 :bar 2))

(prn x)

(defn bar [{:keys [a b c]}]
  [a b c])

(prn (bar {:a 1 :b 2 :c 3}))

(let [[x y z] [1 2 3]]
  (prn x y z))
