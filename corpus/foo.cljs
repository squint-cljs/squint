(ns foo)

(js/console.log "hello")
(js/console.log (+ 1 2 3))

(let [x (do (js/console.log "in do")
            12)]
  (js/console.log "x + 1 =" (inc x)))
