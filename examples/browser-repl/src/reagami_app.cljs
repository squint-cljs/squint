(ns reagami-app
  (:require ["reagami" :as reagami]))

;; Reagami is a zero-dep, Reagent-like hiccup renderer (no React/Preact). It
;; renders plain hiccup vectors - no #jsx, so :jsx-runtime doesn't apply here.
;; State is a plain atom; add-watch + render re-renders on change.
(defonce state (atom {:counter 0}))

(defn counter []
  [:div
   [:div "Counted: " (:counter @state)]
   [:button {:on-click #(swap! state update :counter inc)}
    "Click me!"]])

(defn render []
  (reagami/render (js/document.querySelector "#reagami") [counter]))

(add-watch state ::render (fn [_ _ _ _] (render)))
(render)
