(ns plain
  (:require [ui]))

;; Plain squint, no framework: build an HTML string with #html, set it as
;; innerHTML, and re-attach the click listener on each render. State is an atom;
;; add-watch re-renders. (The most manual of the three.)
(defonce state (atom 0))

(defn render! []
  (let [el (js/document.querySelector "#plain")]
    (set! (.-innerHTML el)
          #html [:div {:style ui/card}
                 [:div {:style (assoc ui/label :color "#0a7d5a")} "plain squint · #html"]
                 [:div {:style ui/counted} "Counted: " @state]
                 [:button {:id "plain-btn" :style ui/btn} "Click me!"]])
    (.addEventListener (.querySelector el "#plain-btn") "click"
                       (fn [_] (swap! state inc)))))

(add-watch state ::render (fn [_ _ _ _] (render!)))
(render!)
