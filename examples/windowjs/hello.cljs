;; translated from https://github.com/windowjs/windowjs/blob/main/examples/hello.js

(set! js/window.title "Hello")

(def canvas js/window.canvas)
(def keep-drawing true)

(js/window.addEventListener
 :click (fn [event]
          (js/console.log "Clicked on" event.x event.y)))

(declare draw)

(js/window.addEventListener
 :keydown (fn [event]
            (js/console.log "Key down:" event.key)
            (when (= event.key " ")
              (set! keep-drawing
                    (not keep-drawing))
              (when keep-drawing
                (js/requestAnimationFrame draw)))))

(defn draw []
  (set! canvas.fillStyle "#023047")
  (canvas.fillRect 0 0 canvas.width canvas.height)
  (set! canvas.fillStyle "#eb005a")
  (canvas.fillRect 100 100 200 200)
  (set! canvas.fillStyle "darkorange")
  (let [y (/ canvas.height 2)
        w canvas.width
        t (Math.cos (/ (js/performance.now) 300))
        x (+ (/ w 2) (* (/ w 4) t))]
    (canvas.save)
    (canvas.translate x y)
    (canvas.rotate (/ (* t Math/PI) 2))
    (canvas.fillRect -100 -100 200 200)
    (canvas.restore))
  (when keep-drawing
    (js/requestAnimationFrame draw)))

(js/requestAnimationFrame draw)
