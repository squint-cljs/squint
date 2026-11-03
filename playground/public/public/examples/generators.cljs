(defn ^:gen foo []
  (js-yield 1)
  (js-yield* [2 3])
  (let [x (inc 3)]
    (js-yield x))
  (let [x (do (js-yield 5)
            6)]
    (js-yield x)))

(vec (foo))
