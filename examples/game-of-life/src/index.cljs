(ns index)

(defn cell [grid-x grid-y size]
  {:size size
   :x grid-x
   :y grid-y
   :alive? (> (js/Math.random) 0.5)})

(defn draw-cell [ctx [_ {:keys [alive? x y size]}]]
  (.beginPath ctx)
  (.arc ctx (* x size) (* y size) (/ size 2) 0 (* 2 js/Math.PI))
  (set! (.-fillStyle ctx) (if alive? "#117855" "#303030"))
  (.fill ctx)
  (.stroke ctx)
  (.closePath ctx))

(defn create-grid [rows columns cell-size]
  (into {}
        (for [x (range rows)
              y (range columns)]
          [[x y] (cell x y cell-size)])))

(defn clear-screen [ctx canvas]
  (set! (.-fillStyle ctx) "#303030")
  (.fillRect ctx 0 0 (.-width canvas) (.-height canvas)))

(defn render [{:keys [ctx grid canvas]}]
  (clear-screen ctx canvas)
  (doseq [cell grid]
    (draw-cell ctx cell)))

(defn get-cell [grid x y max-x max-y]
  (let [x (cond (< x 0) max-x (> x max-x) 0 :else x)
        y (cond (< y 0) max-y (> y max-y) 0 :else y)]
    (get grid [x y])))

(defn compute-grid [{:keys [grid max-x max-y]}]
  (doseq [[_ {:keys [x y alive?] :as cell}] grid]
    (let [live-neighbour-count (->> [(get-cell grid (dec x) (dec y) max-x max-y)
                                     (get-cell grid (inc x) (inc y) max-x max-y)
                                     (get-cell grid (inc x) (dec y) max-x max-y)
                                     (get-cell grid (dec x) (inc y) max-x max-y)
                                     (get-cell grid x (inc y) max-x max-y)
                                     (get-cell grid x (dec y) max-x max-y)
                                     (get-cell grid (dec x) y max-x max-y)
                                     (get-cell grid (inc x) y max-x max-y)]
                                    (map #(get % :alive?))
                                    (filter true?)
                                    (doall)
                                    (count))]
      (assoc! cell :alive?
              (if alive?
                (condp = live-neighbour-count
                  0 false
                  1 false
                  2 true
                  3 true
                  4 false
                  false)
                (= 3 live-neighbour-count))))))

(defn game-loop [state]
  (render state)
  (compute-grid state)
  (js/setTimeout #(js/window.requestAnimationFrame (fn [] (game-loop state))) 60))

(defn game [canvas-id]
  (let [canvas (js/document.getElementById canvas-id)
        ctx (.getContext canvas "2d")
        max-y 50
        max-x (* max-y (/ (.-width canvas) (.-height canvas))) 
        initial-grid (create-grid max-x max-y 10)]
    (game-loop {:canvas canvas
                :ctx ctx
                :max-x (dec max-x)
                :max-y (dec max-y)
                :grid initial-grid})))

(game "canvas")
