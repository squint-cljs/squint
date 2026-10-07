(ns app)

(defn parse-age [s]
  (let [n (js/parseInt s)]
    (if (js/isNaN n)
      (throw (js/Error. (str "not a number: " s)))
      n)))

(defn average-age [people]
  (let [ages (map (fn [p] (parse-age (:age p))) people)]
    (/ (reduce + 0 ages)
       (count ages))))

(prn (average-age [{:age "31"} {:age "29"}]))
(prn (average-age [{:age "31"} {:age "x"}]))
