(ns tic-tac-toe.game)

(defn create-game [{:keys [size]}]
  {:next-player :x
   :size size})

(def next-player {:x :o, :o :x})

(defn winner? [{:keys [tics size]} path]
  ;; squint: maps are plain JS objects, not IFn, so `(map tics path)` would call
  ;; an object. Use `get`.
  (let [marks (remove nil? (map #(get tics %) path))]
    (when (and (= size (count marks)) (= 1 (count (set marks))))
      path)))

(defn get-winning-path [{:keys [size] :as game} y x]
  (or (winner? game (mapv #(vector y %) (range 0 size)))
      (winner? game (mapv #(vector % x) (range 0 size)))
      (winner? game (mapv #(vector % %) (range 0 size)))
      (winner? game (mapv #(vector % (- size % 1)) (range 0 size)))))

(defn maybe-conclude [game y x]
  (if-let [path (get-winning-path game y x)]
    (-> (dissoc game :next-player)
        (assoc :over? true
               :victory {:player (get-in game [:tics [y x]])
                         :path path}))
    (let [tie? (= (count (:tics game)) (* (:size game) (:size game)))]
      (cond-> game
        tie? (dissoc :next-player)
        tie? (assoc :over? true)))))

(defn tic [game y x]
  (let [player (:next-player game)]
    (if (or (get-in game [:tics [y x]])
            (<= (:size game) x)
            (<= (:size game) y))
      game
      (-> game
          (assoc-in [:tics [y x]] player)
          (assoc :next-player (get next-player player))
          (maybe-conclude y x)))))
