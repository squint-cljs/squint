(ns tic-tac-toe.main
  (:require [tic-tac-toe.core :as tic-tac-toe]))

;; squint entry point (:main in squint.edn). The upstream tutorial boots from
;; dev/tic_tac_toe/dev.cljs via shadow's :dev/after-load; squint just runs this
;; module on load.

(defonce store (atom nil))

(tic-tac-toe/main store)
(tic-tac-toe/start-new-game store)
