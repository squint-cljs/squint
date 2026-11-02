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

(rc.rectangle 10 10 200 200)
(rc.circle 80 120 50 {:fill :green})
(rc.circle 80 160 50 {:fill :red})
