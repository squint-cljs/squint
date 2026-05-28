(ns tetris
  (:require ["https://esm.sh/reagami@0.0.36" :as reagami]))

;; Tetris in squint + reagami. Self-contained: copy/paste into the squint
;; playground or any squint browser REPL. Loads reagami from esm.sh; creates
;; the host #tetris div on demand.
;; Board is a vector of rows (top to bottom), each row a vector of cells.
;; A cell is either nil (empty) or a keyword for the piece color.

(def COLS 10)
(def ROWS 20)
(def TICK-MS 500)

(def COLORS
  {:I "#22d3ee"
   :O "#facc15"
   :T "#a855f7"
   :S "#22c55e"
   :Z "#ef4444"
   :L "#f97316"
   :J "#3b82f6"})

;; Pieces as sets of [x y] offsets from the piece origin. Rotation is computed
;; by rotating around (0,0): (x,y) -> (-y, x). O never rotates (handled below).
(def PIECES
  {:I [[-1 0] [0 0] [1 0] [2 0]]
   :O [[0 0] [1 0] [0 1] [1 1]]
   :T [[-1 0] [0 0] [1 0] [0 1]]
   :S [[0 0] [1 0] [-1 1] [0 1]]
   :Z [[-1 0] [0 0] [0 1] [1 1]]
   :L [[-1 0] [0 0] [1 0] [-1 1]]
   :J [[-1 0] [0 0] [1 0] [1 1]]})

(defn empty-board []
  (vec (repeat ROWS (vec (repeat COLS nil)))))

(defn rand-piece []
  (let [k (rand-nth (keys PIECES))]
    {:kind k
     :cells (get PIECES k)
     :pos [(quot COLS 2) 0]}))

(defonce state
  (atom {:board (empty-board)
         :piece (rand-piece)
         :score 0
         :lines 0
         :over? false
         :paused? false}))

(defn piece-cells
  "Absolute [x y] board coords occupied by `piece`."
  [piece]
  (let [[px py] (:pos piece)]
    (map (fn [[dx dy]] [(+ px dx) (+ py dy)]) (:cells piece))))

(defn in-bounds? [[x y]]
  (and (<= 0 x) (< x COLS) (< y ROWS)))

(defn collides?
  "True if any cell of `piece` is out of bounds (sides/bottom) or hits an
  occupied cell on `board`. y < 0 is allowed (spawn above the top)."
  [board piece]
  (some (fn [[x y]]
          (or (< x 0) (>= x COLS) (>= y ROWS)
              (and (>= y 0) (get-in board [y x]))))
        (piece-cells piece)))

(defn rotate-cells [cells]
  (mapv (fn [[x y]] [(- y) x]) cells))

(defn lock-piece
  "Burn `piece` into `board`, returning the new board."
  [board piece]
  (reduce (fn [b [x y]]
            (if (and (>= y 0) (in-bounds? [x y]))
              (assoc-in b [y x] (:kind piece))
              b))
          board
          (piece-cells piece)))

(defn clear-lines
  "Drop full rows; return [new-board cleared-count]."
  [board]
  (let [kept (vec (remove (fn [row] (every? some? row)) board))
        cleared (- ROWS (count kept))
        pad (vec (repeat cleared (vec (repeat COLS nil))))]
    [(into pad kept) cleared]))

(def SCORES {0 0 1 100 2 300 3 500 4 800})

(defn try-move [s dx dy]
  (let [p' (update (:piece s) :pos (fn [[x y]] [(+ x dx) (+ y dy)]))]
    (if (collides? (:board s) p') s (assoc s :piece p'))))

(defn try-rotate [s]
  (let [p (:piece s)
        p' (if (= :O (:kind p)) p (update p :cells rotate-cells))]
    (if (collides? (:board s) p') s (assoc s :piece p'))))

(defn step-down
  "Gravity tick: move piece down, or lock + clear + spawn next. Sets :over?
  when the freshly spawned piece can't fit (game over)."
  [s]
  (let [p (:piece s)
        p' (update p :pos (fn [[x y]] [x (inc y)]))]
    (if (collides? (:board s) p')
      (let [locked (lock-piece (:board s) p)
            [board' n] (clear-lines locked)
            next-piece (rand-piece)
            over? (boolean (collides? board' next-piece))]
        (-> s
            (assoc :board board'
                   :piece next-piece
                   :over? over?)
            (update :score + (get SCORES n 0))
            (update :lines + n)))
      (assoc s :piece p'))))

(defn hard-drop [s]
  (loop [s s]
    (let [p (:piece s)
          p' (update p :pos (fn [[x y]] [x (inc y)]))]
      (if (collides? (:board s) p')
        (step-down s)
        (recur (assoc s :piece p'))))))

;; -------------------------------------------------------------- rendering ----

(def CELL 24)

(defn render-board
  "Build a [ROWS x COLS] view by overlaying the active piece on the locked
  board, then render as a grid."
  [s]
  (let [b (:board s)
        piece (:piece s)
        active (into {} (map (fn [c] [c (:kind piece)]) (piece-cells piece)))
        cell-color (fn [x y]
                     (or (get active [x y])
                         (get-in b [y x])))]
    [:div {:style {:display "inline-grid"
                   :grid-template-columns (str "repeat(" COLS ", " CELL "px)")
                   :grid-template-rows (str "repeat(" ROWS ", " CELL "px)")
                   :gap "1px"
                   :background "#111"
                   :padding "4px"
                   :border-radius "6px"}}
     (for [y (range ROWS)
           x (range COLS)
           :let [k (cell-color x y)]]
       [:div {:key (str x "-" y)
              :style {:width (str CELL "px")
                      :height (str CELL "px")
                      :background (or (get COLORS k) "#1f2937")
                      :border-radius "3px"}}])]))

(defn panel [s]
  [:div {:style {:font "14px system-ui"
                 :color "#e5e7eb"
                 :margin-left "16px"
                 :min-width "160px"}}
   [:div {:style {:font "600 11px system-ui"
                  :letter-spacing ".08em"
                  :text-transform "uppercase"
                  :color "#fb7185"
                  :margin-bottom "8px"}}
    "tetris - reagami"]
   [:div "Score: " (:score s)]
   [:div "Lines: " (:lines s)]
   (when (:over? s)
     [:div {:style {:margin-top "10px" :color "#fca5a5"}} "GAME OVER"])
   (when (:paused? s)
     [:div {:style {:margin-top "10px" :color "#fcd34d"}} "PAUSED"])
   [:div {:style {:margin-top "12px" :font-size "12px" :color "#9ca3af"
                  :line-height "1.6"}}
    "<- -> move" [:br]
    "up rotate" [:br]
    "down soft drop" [:br]
    "space hard drop" [:br]
    "p pause, r reset"]])

(defn root []
  (let [s @state]
    [:div {:style {:display "inline-flex"
                   :align-items "flex-start"
                   :font-family "system-ui, sans-serif"
                   :background "#0b1020"
                   :padding "16px"
                   :border-radius "10px"
                   :margin "10px"}}
     [render-board s]
     [panel s]]))

(defn host-el
  "Find or create the #tetris mount point. Hides every sibling so the
  playground editor/output panes don't steal arrow-key focus while playing.
  Centers the game horizontally so wide screens don't show a giant dark gutter.
  Paints a deep indigo gradient on body so the page matches the game's vibe."
  []
  (let [el (or (js/document.querySelector "#tetris")
               (let [el (js/document.createElement "div")]
                 (set! (.-id el) "tetris")
                 (.appendChild js/document.body el)
                 el))]
    (set! (.. js/document.body -style -background)
          "linear-gradient(135deg,#0b1020,#1e1b4b)")
    (set! (.. js/document.body -style -minHeight) "100vh")
    (set! (.. js/document.body -style -margin) "0")
    (set! (.. el -style -display) "flex")
    (set! (.. el -style -justifyContent) "center")
    (set! (.. el -style -paddingTop) "32px")
    (doseq [sib (js/Array.from (.-children js/document.body))]
      (when (not= sib el)
        (set! (.. sib -style -display) "none")))
    el))

(defn render! []
  (reagami/render (host-el) [root]))

(defonce _watch
  (add-watch state ::render (fn [_ _ _ _] (render!))))

;; --------------------------------------------------------------- controls ----

(defn reset-game! []
  (cljs.core/reset! state {:board (empty-board)
                           :piece (rand-piece)
                           :score 0
                           :lines 0
                           :over? false
                           :paused? false}))

(defn on-key [e]
  (let [k (.-key e)
        s @state]
    (when-not (:over? s)
      (case k
        "ArrowLeft"  (do (.preventDefault e) (swap! state try-move -1 0))
        "ArrowRight" (do (.preventDefault e) (swap! state try-move 1 0))
        "ArrowDown"  (do (.preventDefault e) (swap! state try-move 0 1))
        "ArrowUp"    (do (.preventDefault e) (swap! state try-rotate))
        " "          (do (.preventDefault e) (swap! state hard-drop))
        "p"          (swap! state update :paused? not)
        "r"          (reset-game!)
        nil))
    (when (and (:over? s) (= "r" k)) (reset-game!))))

;; Keep one listener across hot reloads.
(defonce _key
  (do (.addEventListener js/window "keydown" on-key)
      true))

(defonce _timer
  (js/setInterval
   (fn []
     (let [s @state]
       (when-not (or (:over? s) (:paused? s))
         (swap! state step-down))))
   TICK-MS))

(render!)
