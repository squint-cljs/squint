(ns index)

(def card-style
  "font-family:system-ui,sans-serif;border:1px solid #e2e2e2;border-radius:10px;padding:14px 16px;margin:10px;max-width:280px;box-shadow:0 1px 2px rgba(0,0,0,.06)")

(def btn-style
  "font:14px system-ui;padding:4px 10px;border:1px solid #ccc;border-radius:6px;cursor:pointer")

;; Plain squint, no framework: build an HTML string with #html, set it as
;; innerHTML, and re-attach the click listener on each render. State is an atom;
;; add-watch re-renders. (The most manual of the three.)
(def state (atom 0))

(defn render! []
  (let [el (js/document.querySelector "#app")]
    (set! (.-innerHTML el)
          #html [:div {:style card-style}
                 [:div {:style "font:600 11px system-ui;letter-spacing:.08em;text-transform:uppercase;margin-bottom:8px;color:#0a7d5a"}
                  "plain squint · #html"]
                 [:div {:style "font-size:14px;margin-bottom:8px"} "Counted: " @state]
                 [:button {:id "app-btn" :style btn-style} "Click me!"]])
    (.addEventListener (.querySelector el "#app-btn") "click"
                       (fn [_] (swap! state inc)))))

(add-watch state ::render (fn [_ _ _ _] (render!)))
(render!)
