(ns readers)

(defn point [[x y]]
  (list 'str "point " x " " y))

(defn inst [s]
  (list 'str "inst " s))
