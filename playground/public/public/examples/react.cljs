(require '["react" :as react])
(require '["react-dom" :as rdom])

(defonce component-state (atom 0))

(defn Bar [{:keys [firstname lastname] :as props}]
  (let [[clicks setClicks] (react/useState @component-state)]
    #jsx [:<>
          [:span firstname " " lastname]
          [:div "You clicked " clicks " times!"]
          [:button {:onClick #(setClicks (swap! component-state inc))}
           "Click me"]]))

(defn Foo []
  #jsx [:div "Hello, "
        (let [m (assoc {:firstname "Michiel"} :lastname "Borkent")]
          #jsx [Bar {:& m}])])

;; Squint Playground convention:
;; DOM always goes into the element with the id "app".
(def app (or (js/document.getElementById "app")
             (doto (js/document.createElement "div")
               (set! -id "app")
               (js/document.body.prepend))))

(defonce root (rdom/createRoot app))

(.render root #jsx [Foo])
