(ns index
  (:require
   [MyComponent :as MyComponent]
   ["react-dom/client" :as rdom]))

(def root (rdom/createRoot (js/document.getElementById "app")))
(.render root #jsx [MyComponent/MyComponent])

