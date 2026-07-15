(ns tic-tac-toe.main
  (:require [tic-tac-toe.core :as tic-tac-toe]))

;; squint entry point (:main in squint.edn). The upstream tutorial boots from
;; dev/tic_tac_toe/dev.cljs via shadow's :dev/after-load; squint just runs this
;; module on load.

(defonce store (atom nil))

;; touching the store re-runs the render watch with the freshly loaded code
(defn ^:dev/after-load re-render []
  (swap! store identity))

(tic-tac-toe/main store)
(tic-tac-toe/start-new-game store)
