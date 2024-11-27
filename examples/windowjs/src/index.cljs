(ns index)

(def canvas (js/document.getElementById "anchor"))
(def ctx (.getContext canvas "2d"))

(set! js/window.title "Hello")

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
  (set! ctx.fillStyle "#023047")
  (ctx.fillRect 0 0 canvas.width canvas.height)
  (set! ctx.fillStyle "#eb005a")
  (ctx.fillRect 100 100 200 200)
  (set! ctx.fillStyle "darkorange")
  (let [y (/ canvas.height 2)
        w canvas.width
        t (js/Math.cos (/ (js/performance.now) 300))
        x (+ (/ w 2) (* (/ w 4) t))]
    (ctx.save)
    (ctx.translate x y)
    (ctx.rotate (/ (* t Math/PI) 2))
    (ctx.fillRect -100 -100 200 200)
    (ctx.restore))
  (when keep-drawing
    (js/requestAnimationFrame draw)))

(js/requestAnimationFrame draw)