(ns app
  (:require ["preact" :refer [render]]))

(defn hot-fn
  "Redefined by the e2e :hot :ws test."
  []
  1)

(defonce state (atom {:a 1}))

(declare render!)

(defn App []
  #jsx [:div
        [:button {:id "btn"
                  :onClick (fn []
                             (swap! state update :a inc)
                             (render!))}
         "Click me"]
        [:div "Counter " (str (:a @state))]])

(defn render! []
  (render #jsx [App] (js/document.getElementById "app")))

(render!)

(defn ^:dev/after-load re-render [] (render!))
