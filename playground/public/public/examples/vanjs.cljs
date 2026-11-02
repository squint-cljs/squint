(require '["https://cdn.jsdelivr.net/gh/vanjs-org/van/public/van-1.2.7.min.js$default"
           :as van])

(def button van/tags.button)
(def span van/tags.span)

(defn Counter []
  (let [counter (van/state 0)]
    (span "\u2764" counter " "
      (button {:onclick #(set! (.-val counter) (dec (.-val counter)))}
        "\ud83d\udc4e")
      (button {:onclick #(set! (.-val counter) (inc (.-val counter)))}
        "\ud83d\udc4d"))))

;; Squint Playground convention:
;; DOM always goes into the element with the id "app".
(def app (or (js/document.getElementById "app")
             (doto (js/document.createElement "div")
               (set! -id "app")
               (js/document.body.prepend))))

(.replaceChildren app (Counter))
