(require '["https://esm.sh/roughjs@4.6.6$default" :as rough])

;; Squint Playground convention:
;; DOM always goes into the element with the id "app".
(def app (or (js/document.getElementById "app")
             (doto (js/document.createElement "div")
               (set! -id "app")
               (js/document.body.prepend))))

(def canvas (or (js/document.querySelector "#canvas")
                (let [c (doto (js/document.createElement "canvas")
                          (set! -id "canvas")
                          (set! -width 250)
                          (set! -height 250))]
                  (.append app c)
                  c)))

(def rc (rough/canvas canvas))

;; Rough strokes in black by default, which vanishes on a dark page. Draw in the
;; page's text color instead, read from #app, and redraw when the theme changes.
(defn draw! []
  (let [ink (.-color (js/getComputedStyle app))]
    (.clearRect (.getContext canvas "2d") 0 0 (.-width canvas) (.-height canvas))
    (rc.rectangle 10 10 200 200 {:stroke ink})
    (rc.circle 80 120 50 {:stroke ink :fill :green})
    (rc.circle 80 160 50 {:stroke ink :fill :red})))

(when js/window.roughThemeObserver
  (.disconnect js/window.roughThemeObserver))
;; the page's text color fades over 200ms, so redraw once it has settled
(set! js/window.roughThemeObserver (js/MutationObserver. (fn [] (js/setTimeout draw! 250))))
(.observe js/window.roughThemeObserver js/document.documentElement
          {:attributes true :attributeFilter ["data-theme"]})

(draw!)
