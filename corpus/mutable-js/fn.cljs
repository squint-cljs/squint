(ns fn)

(defn foo [x y z]
  (let [x (+ x 10)]
    [x y z]))

(prn (foo 1 2 3))

(def x (assoc! {} :foo 1 :bar 2))

(prn x)
