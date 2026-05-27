(ns reagami
  (:require ["reagami" :as r]
            [ui]))

;; Reagami is a zero-dep, Reagent-like hiccup renderer (no React/Preact). It
;; renders plain hiccup vectors - no #jsx, so :jsx-runtime doesn't apply here.
;; State is a plain atom; add-watch + render re-renders on change.
(defonce state (atom {:counter 0}))

(defn counter []
  [:div {:style ui/card}
   [:div {:style (assoc ui/label :color "#d2691e")} "reagami · hiccup"]
   [:div {:style ui/counted} "Counted: " (:counter @state)]
   [:button {:style ui/btn
             :on-click #(swap! state update :counter inc)}
    "Click me!"]])

(defn render []
  (r/render (js/document.querySelector "#reagami") [counter]))

(add-watch state ::render (fn [_ _ _ _] (render)))
(render)
