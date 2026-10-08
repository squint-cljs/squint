(require '["react" :as react])
(require '["react-dom" :as rdom])

(def empty-board [[\- \- \-]
                  [\- \- \-]
                  [\- \- \-]])

(def init-state {:board empty-board :player \X})

(defn get-board-cell
  ([board row col]
   (get-in board [row col])))

(defn get-player [app-state]
  (-> app-state :game-state :player))

(defn other-player [player]
  (if (= player \X) \O \X))

(defn winner-in-rows? [board player]
  (some (fn [row] (every? (fn [c] (= c player)) row)) board))

(defn transposed-board [board]
  (vec (apply map vector board)))

(defn winner-in-cols? [board player]
  (winner-in-rows? (transposed-board board) player))

(defn winner-in-diagonals? [board player]
  (let [diag-coords [[[0 0] [1 1] [2 2]]
                     [[0 2] [1 1] [2 0]]]]
    (some (fn [coords]
            (every? (fn [coord]
                      (= player (apply get-board-cell board coord)))
              coords))
      diag-coords)))

(defn winner
  "checks if there is a winner. when called with no args, checks for player X and player O.
returns the character for the winning player, nil if there is no winner"
  ([board]
   (or (winner board \X)
     (winner board \O)))
  ([board player]
   (if (or (winner-in-rows? board player)
         (winner-in-cols? board player)
         (winner-in-diagonals? board player))
     player)))

(defn full-board?
  [board]
  (let [all-cells (apply concat board)]
    (not-any? #(= % \-) all-cells)))

(defn new-state [row col old-state]
  (if (and (= (get-board-cell (:board old-state) row col) \-)
        (not (winner (:board old-state))))
    {:board (assoc-in (:board old-state) [row col] (:player old-state))
     :player (other-player (:player old-state))}
    old-state))

(defn board []
  (let [[state setState] (react/useState init-state)
        {:keys [player board]} state]
    #jsx
    [:div {:key "winner"}
     (str "The winner is " (winner board))
     [:div {:key "game"
            :style {:color "#666"
                    :font-family "Arial"}}
      [:table {:style {:width "auto"}}
       (for [i (range 0 3)]
         #jsx
           [:tr {:key i}
            (for [j (range 0 3)]
              #jsx [:td {:key j
                         :style {:padding "1px"
                                 :color "#b8b8b8"}}
                    [:div {
                         :style {:border "1px solid currentColor"
                                 :margin "1px"
                                 :height "50px"
                                 :width "50px"
                                 :line-height "50px"
                                 :text-align "center"}
                         :onClick
                         (fn [_]
                           (if (and (= "-" (get-in board [i j]))
                                 (not (winner board))
                                 (not (full-board? board)))
                             (setState (->
                                         state
                                         (update :player other-player)
                                         (update :board assoc-in [i j] player)))))}
                     (get-in board [i j])]])])]]]))

(defn render-board []
  (let [elt (or (js/document.getElementById "app")
              (doto (js/document.createElement "div")
                (set! -id "div")
                (js/document.body.prepend)))
        root (rdom/createRoot elt)]
    (.render root #jsx [board])))

(render-board)
