(ns main
  (:require ["idiomorph" :refer [Idiomorph] :rename {Idiomorph I}]))

(def size 20) ;; grid size (20x20)
(def cell 10) ;; each cell = 10px

(defonce state
  (atom {:snake [[5 10] [4 10] [3 10]] ; initial snake body
         :dir [1 0] ; moving right
         :food [15 10] ; initial food
         :alive? true}))

(defn random-food []
  [(rand-int size) (rand-int size)])

(defn move-snake []
  (swap! state
    (fn [{:keys [snake dir food alive?] :as st}]
      (if (not alive?)
             st
             (let [head (vec [(+ (nth (first snake) 0) (nth dir 0))
                              (+ (nth (first snake) 1) (nth dir 1))])
                   ate? (= head food)
                   new-snake (vec (cons head (if ate? snake (butlast snake))))
                   x (nth head 0)
                   y (nth head 1)
                   hit-wall? (or (< x 0) (< y 0)
                               (>= x size) (>= y size))
                   hit-self? (some #(= head %) (rest new-snake))]
               (cond
                 hit-wall? (assoc st :alive? false)
                 hit-self? (assoc st :alive? false)
                 ate? (assoc st :snake new-snake :food (random-food))
                 :else (assoc st :snake new-snake)))))))

;; Timer loop
(js/setInterval move-snake 200)

;; Keyboard controls
(.addEventListener js/window "keydown"
  (fn [e]
    (let [key (.-key e)]
       (swap! state update :dir
         (fn [dir]
           (case key
             "ArrowUp" (if (= [0 1] dir) dir [0 -1])
             "ArrowDown" (if (= [0 -1] dir) dir [0 1])
             "ArrowLeft" (if (= [1 0] dir) dir [-1 0])
             "ArrowRight" (if (= [-1 0] dir) dir [1 0])
             dir))))))

(defn cell-rect [[x y] color]
  #html
  [:rect {:x (* x cell) :y (* y cell)
          :width cell :height cell
          :fill color}])

(defn game-board []
  (let [{:keys [snake food alive?]} @state]
    #html
    [:svg {:width (* size cell) :height (* size cell)
           :style {:border "1px solid black"
                   :background "#eef"}}
     ;; snake
     (for [part snake]
       (cell-rect part "green"))
     ;; food
     (cell-rect food "red")
     ;; game over overlay
     (when (not alive?)
       #html
       [:text {:x 50 :y 100 :font-size 20 :fill "black"} "Game Over!"])]))

(defn game []
  #html [:div
         [:p [:a {:href "https://squint-cljs.github.io/squint/?src=KHJlcXVpcmUgJ1siaHR0cHM6Ly9lc20uc2gvaWRpb21vcnBoIiA6cmVmZXIgW0lkaW9tb3JwaF0gOnJlbmFtZSB7SWRpb21vcnBoIEl9XSkKCihkZWYgc2l6ZSAyMCkgOzsgZ3JpZCBzaXplICgyMHgyMCkKKGRlZiBjZWxsIDEwKSA7OyBlYWNoIGNlbGwgPSAxMHB4CgooZGVmb25jZSBzdGF0ZQogIChhdG9tIHs6c25ha2UgW1s1IDEwXSBbNCAxMF0gWzMgMTBdXSA7IGluaXRpYWwgc25ha2UgYm9keQogICAgICAgICA6ZGlyIFsxIDBdIDsgbW92aW5nIHJpZ2h0CiAgICAgICAgIDpmb29kIFsxNSAxMF0gOyBpbml0aWFsIGZvb2QKICAgICAgICAgOmFsaXZlPyB0cnVlfSkpCgooZGVmbiByYW5kb20tZm9vZCBbXQogIFsocmFuZC1pbnQgc2l6ZSkgKHJhbmQtaW50IHNpemUpXSkKCihkZWZuIG1vdmUtc25ha2UgW10KICAoc3dhcCEgc3RhdGUKICAgIChmbiBbezprZXlzIFtzbmFrZSBkaXIgZm9vZCBhbGl2ZT9dIDphcyBzdH1dCiAgICAgIChpZiAobm90IGFsaXZlPykKICAgICAgICAgICAgIHN0CiAgICAgICAgICAgICAobGV0IFtoZWFkICh2ZWMgWygrIChudGggKGZpcnN0IHNuYWtlKSAwKSAobnRoIGRpciAwKSkKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgKCsgKG50aCAoZmlyc3Qgc25ha2UpIDEpIChudGggZGlyIDEpKV0pCiAgICAgICAgICAgICAgICAgICBhdGU%2FICg9IGhlYWQgZm9vZCkKICAgICAgICAgICAgICAgICAgIG5ldy1zbmFrZSAodmVjIChjb25zIGhlYWQgKGlmIGF0ZT8gc25ha2UgKGJ1dGxhc3Qgc25ha2UpKSkpCiAgICAgICAgICAgICAgICAgICB4IChudGggaGVhZCAwKQogICAgICAgICAgICAgICAgICAgeSAobnRoIGhlYWQgMSkKICAgICAgICAgICAgICAgICAgIGhpdC13YWxsPyAob3IgKDwgeCAwKSAoPCB5IDApCiAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAoPj0geCBzaXplKSAoPj0geSBzaXplKSkKICAgICAgICAgICAgICAgICAgIGhpdC1zZWxmPyAoc29tZSAjKD0gaGVhZCAlKSAocmVzdCBuZXctc25ha2UpKV0KICAgICAgICAgICAgICAgKGNvbmQKICAgICAgICAgICAgICAgICBoaXQtd2FsbD8gKGFzc29jIHN0IDphbGl2ZT8gZmFsc2UpCiAgICAgICAgICAgICAgICAgaGl0LXNlbGY%2FIChhc3NvYyBzdCA6YWxpdmU%2FIGZhbHNlKQogICAgICAgICAgICAgICAgIGF0ZT8gKGFzc29jIHN0IDpzbmFrZSBuZXctc25ha2UgOmZvb2QgKHJhbmRvbS1mb29kKSkKICAgICAgICAgICAgICAgICA6ZWxzZSAoYXNzb2Mgc3QgOnNuYWtlIG5ldy1zbmFrZSkpKSkpKSkKCjs7IFRpbWVyIGxvb3AKKGpzL3NldEludGVydmFsIG1vdmUtc25ha2UgMjAwKQoKOzsgS2V5Ym9hcmQgY29udHJvbHMKKC5hZGRFdmVudExpc3RlbmVyIGpzL3dpbmRvdyAia2V5ZG93biIKICAoZm4gW2VdCiAgICAobGV0IFtrZXkgKC4ta2V5IGUpXQogICAgICAgKHN3YXAhIHN0YXRlIHVwZGF0ZSA6ZGlyCiAgICAgICAgIChmbiBbZGlyXQogICAgICAgICAgIChjYXNlIGtleQogICAgICAgICAgICAgICAgICAiQXJyb3dVcCIgKGlmICg9IGRpciBbMCAxXSkgZGlyIFswIC0xXSkKICAgICAgICAgICAgICAgICAgIkFycm93RG93biIgKGlmICg9IGRpciBbMCAtMV0pIGRpciBbMCAxXSkKICAgICAgICAgICAgICAgICAgIkFycm93TGVmdCIgKGlmICg9IGRpciBbMSAwXSkgZGlyIFstMSAwXSkKICAgICAgICAgICAgICAgICAgIkFycm93UmlnaHQiIChpZiAoPSBkaXIgWy0xIDBdKSBkaXIgWzEgMF0pCiAgICAgICAgICAgICAgICAgIGRpcikpKSkpKQoKKGRlZm4gY2VsbC1yZWN0IFtbeCB5XSBjb2xvcl0KICAjaHRtbAogIFs6cmVjdCB7OnggKCogeCBjZWxsKSA6eSAoKiB5IGNlbGwpCiAgICAgICAgICA6d2lkdGggY2VsbCA6aGVpZ2h0IGNlbGwKICAgICAgICAgIDpmaWxsIGNvbG9yfV0pCgooZGVmbiBnYW1lLWJvYXJkIFtdCiAgKGxldCBbezprZXlzIFtzbmFrZSBmb29kIGFsaXZlP119IEBzdGF0ZV0KICAgICNodG1sCiAgICBbOnN2ZyB7OndpZHRoICgqIHNpemUgY2VsbCkgOmhlaWdodCAoKiBzaXplIGNlbGwpCiAgICAgICAgICAgOnN0eWxlIHs6Ym9yZGVyICIxcHggc29saWQgYmxhY2siCiAgICAgICAgICAgICAgICAgICA6YmFja2dyb3VuZCAiI2VlZiJ9fQogICAgIDs7IHNuYWtlCiAgICAgKGZvciBbcGFydCBzbmFrZV0KICAgICAgIChjZWxsLXJlY3QgcGFydCAiZ3JlZW4iKSkKICAgICA7OyBmb29kCiAgICAgKGNlbGwtcmVjdCBmb29kICJyZWQiKQogICAgIDs7IGdhbWUgb3ZlciBvdmVybGF5CiAgICAgKHdoZW4gKG5vdCBhbGl2ZT8pCiAgICAgICAjaHRtbAogICAgICAgWzp0ZXh0IHs6eCA1MCA6eSAxMDAgOmZvbnQtc2l6ZSAyMCA6ZmlsbCAiYmxhY2sifSAiR2FtZSBPdmVyISJdKV0pKQoKKGRlZiByb290IChvcgogICAgICAgICAgICAoanMvZG9jdW1lbnQucXVlcnlTZWxlY3RvciAiI2NvbnRhaW5lciIpCiAgICAgICAgICAgIChkb3RvIChqcy9kb2N1bWVudC5jcmVhdGVFbGVtZW50ICJkaXYiKQogICAgICAgICAgICAgIChzZXQhIC1pZCAiY29udGFpbmVyIikKICAgICAgICAgICAgICAoanMvZG9jdW1lbnQuYm9keS5wcmVwZW5kKSkpKQoKKGRlZm4gbW9ycGggW10KICAoSS9tb3JwaCByb290IChzdHIgKGdhbWUtYm9hcmQpKQogICAgezptb3JwaFN0eWxlIDppbm5lckhUTUx9KSkKCihhZGQtd2F0Y2ggc3RhdGUgOnN0YXRlIChmbiBbXyBfIF8gX10KICAgICAgICAgICAgICAgICAgICAgICAgICAobW9ycGgpKSkKCihtb3JwaCk%3D"}
             "View source in squint playground"]]
         (game-board)])

(defn morph []
  (I/morph (js/document.querySelector "#app") (str (game))
    {:morphStyle :innerHTML}))

(add-watch state :state (fn [_ _ _ _]
                          (morph)))

(morph)
