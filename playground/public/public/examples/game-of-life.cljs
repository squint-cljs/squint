(ns index)

(defn cell [grid-x grid-y size]
  {:size size
   :x grid-x
   :y grid-y
   :alive? (> (js/Math.random) 0.5)})

;; The dead cell and background color follows the page: white on a light background,
;; dark gray on a dark one. Read from #app's computed background each frame.
(defn light-background? []
  (let [bg (.-backgroundColor (js/getComputedStyle (js/document.getElementById "app")))
        [r g b] (map js/parseFloat (re-seq #"[\d.]+" bg))]
    (> (+ (* 0.299 r) (* 0.587 g) (* 0.114 b)) 128)))

(def colors (atom {:dead "#303030" :stroke "#000"}))

(defn draw-cell [ctx [_ {:keys [alive? x y size]}]]
  (.beginPath ctx)
  (.arc ctx (* x size) (* y size) (/ size 2) 0 (* 2 js/Math.PI))
  (set! (.-strokeStyle ctx) (:stroke @colors))
  (set! (.-fillStyle ctx) (if alive? "#117855" (:dead @colors)))
  (.fill ctx)
  (.stroke ctx)
  (.closePath ctx))

(defn create-grid [rows columns cell-size]
  (into {}
        (for [x (range rows)
              y (range columns)]
          [[x y] (cell x y cell-size)])))

(defn clear-screen [ctx canvas]
  (reset! colors (if (light-background?)
                   {:dead "#fff" :stroke "#e8e8e8"}
                   {:dead "#303030" :stroke "#000"}))
  (set! (.-fillStyle ctx) (:dead @colors))
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

;; Squint Playground convention:
;; DOM always goes into the element with the id "app".
(def app (or (js/document.getElementById "app")
             (doto (js/document.createElement "div")
               (set! -id "app")
               (js/document.body.prepend))))

(defonce make-canvas
  (do (.append app
        (doto (js/document.createElement "canvas")
          (set! -id "canvas")
          (set! -height (* js/window.innerHeight 0.25))
          (set! -width (* js/window.innerWidth 0.25))))
    true))

(game "canvas")
