(ns wordle)

(def board-state (atom []))
(def counter (atom 0))
(def attempt (atom 0))
(def word-of-the-day (atom "hello"))

(defn write-letter [cell letter]
  (set! (.-textContent cell) letter))

(defn make-cell []
  (let [cell (js/document.createElement "div")]
    (set! (.-className cell) "cell")
    cell))

(defn make-board [n]
  (let [board (js/document.createElement "div")]
    (set! (.-className board) "board")
    ;; adding cells
    (doseq [_ (range n)]
      (let [cell (make-cell)]
        (swap! board-state conj cell)
        (.appendChild board cell)))
    board))

(defn get-letter [cell]
  (.-textContent cell))

(defn color-cell [idx cell]
  (let [color (fn [el color]
                (set! (-> el .-style .-backgroundColor)
                  color))
        letter (get-letter cell)]
    (cond
      (= letter (nth @word-of-the-day idx))
      (color cell "green")

      (contains? (set @word-of-the-day) letter)
      (color cell "aqua")

      :else
      (color cell "#333333"))))

(defn check-solution [cells]
  (doseq [[idx cell] (map-indexed vector cells)]
    (color-cell idx cell))
  (= (mapv get-letter cells)
    (vec @word-of-the-day)))

(defn user-input [key]
  (let [start (* 5 @attempt)
        end (* 5 (inc @attempt))]
    (cond
      (and (re-matches #"[a-z]" key)
        (< @counter end))
      (do
        (write-letter (nth @board-state @counter) key)
        (swap! counter inc))

      (and (= key "backspace")
        (> @counter start))
      (do
        (swap! counter dec)
        (write-letter (nth @board-state @counter) ""))

      (and (= key "enter")
        (= @counter end))
      (do
        (when (check-solution (subvec @board-state start end))
          (js/alert "You won"))
        (swap! attempt inc))

      )))

(defonce listener (atom nil))

(defn ^:dev/before-load unmount []
  (js/document.removeEventListener "keydown" @listener)
  (let [app (js/document.getElementById "app")]
    (set! (.-innerHTML app) "")))

(defn element [tag id child-of prepend?]
  (or (js/document.getElementById id)
    (let [elt (js/document.createElement tag)
          parent (if child-of (js/document.querySelector child-of)
                   js/document.body)]
      (set! elt -id id)
      (if prepend?
        (.prepend parent elt)
        (.appendChild parent elt))
      elt)))

(set! js/globalThis -elt element)

(def styles "<style>
      #app {
          background-color: #121212;
          border: 1px solid grey;
          border-radius: 10px;
          margin-bottom: 10px
      }
      .board {
          display: grid;
          grid-template-columns: repeat(5, 60px);
      }
      .cell {
          color: white;
          font-weight: bold;
          display: flex;
          justify-content: center;
          align-items: center;
          font-family: sans-serif;
          font-size: 1.5rem;
          width: 50px;
          height: 50px;
          margin: 10px;
          border: 2px solid gray;
      }
</style>")

;; Squint Playground convention:
;; DOM always goes into the element with the id "app".
(defn mount []
  (let [app (element "div" "app" "body" true)
        style (element "style" "styles" "head")
        board (make-board 30)
        input-listener
        (fn [e]
          (user-input (.toLowerCase (.-key e))))]
    (.appendChild app board)
    (set! style -outerHTML styles)
    (reset! listener input-listener)
    (js/document.addEventListener
      "keydown"
      input-listener)))

(mount)
