(ns tic-tac-toe.core
  (:require [replicant.dom :as r]
            [tic-tac-toe.game :as game]
            [tic-tac-toe.ui :as ui]))

(defn start-new-game [store]
  (reset! store (game/create-game {:size 3})))

(defn main [store]
  (let [el (js/document.getElementById "app")]

    ;; Globally handle DOM events
    (r/set-dispatch!
     (fn [_ [action & args]]
       (case action
         :tic (apply swap! store game/tic args)
         :reset (start-new-game store))))

    ;; Render on every change
    (add-watch store ::render
               (fn [_ _ _ game]
                 (->> (ui/game->ui-data game)
                      ui/render-game
                      (r/render el))))))
