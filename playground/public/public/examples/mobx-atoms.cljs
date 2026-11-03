(ns counter
  (:require [squint.core :refer [defclass]]
   ["react" :as react]
   ["react-dom" :as rdom]
   ["https://esm.sh/mobx@6.12.0" :refer [makeAutoObservable]]
   ;; one copy of each library: ?external=react uses the "react" import
   ;; above, and deps= pins the same mobx as the line above. With two copies,
   ;; hooks fail (React) or the view never updates (mobx).
   ["https://esm.sh/mobx-react-lite@4.0.5?external=react&deps=mobx@6.12.0" :refer [observer]]))

(def my-timer
  (makeAutoObservable
    (atom {:counter 0})))

(defn inc-counter []
  (swap! my-timer update :counter inc))

(defn TimerView []
  #jsx [:span "Seconds passed: "
        (:counter @my-timer)])

(def TimerView (observer TimerView))

;; Squint Playground convention:
;; DOM always goes into the element with the id "app".
(def app (or (js/document.getElementById "app")
             (doto (js/document.createElement "div")
               (set! -id "app")
               (js/document.body.prepend))))
(defonce root (rdom/createRoot app))
(.render root #jsx [TimerView])

(defonce create-interval
  (do
    (js/setInterval inc-counter 1000)
    true))
