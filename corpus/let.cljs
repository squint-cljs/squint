(ns foo)

(def log js/console.log)

(log "hello")
(log (+ 1 2 3))

(let [y (let [x (do (log "in do")
                    12)]
          (log "x + 1 =" (inc x))
          (+ x 13))
      ;; shadowing
      inc "inc"]
  (log "y =" y inc))
