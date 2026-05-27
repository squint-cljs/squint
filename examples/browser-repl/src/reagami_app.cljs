(ns reagami-app
  (:require ["reagami" :as reagami]))

;; Reagami is a zero-dep, Reagent-like hiccup renderer (no React/Preact). It
;; renders plain hiccup vectors - no #jsx, so :jsx-runtime doesn't apply here.
;; State is a plain atom; add-watch + render re-renders on change.
(defonce state (atom {:counter 0}))

(def card-style
  "font-family:system-ui,sans-serif;border:1px solid #e2e2e2;border-radius:10px;padding:14px 16px;margin:10px;max-width:280px;box-shadow:0 1px 2px rgba(0,0,0,.06)")

(defn counter []
  [:div {:style card-style}
   [:div {:style "font:600 11px system-ui;letter-spacing:.08em;text-transform:uppercase;margin-bottom:8px;color:#d2691e"}
    "reagami · hiccup"]
   [:div {:style "font-size:14px;margin-bottom:8px"} "Counted: " (:counter @state)]
   [:button {:style "font:14px system-ui;padding:4px 10px;border:1px solid #ccc;border-radius:6px;cursor:pointer"
             :on-click #(swap! state update :counter inc)}
    "Click me!"]])

(defn render []
  (reagami/render (js/document.querySelector "#reagami") [counter]))

(add-watch state ::render (fn [_ _ _ _] (render)))
(render)
