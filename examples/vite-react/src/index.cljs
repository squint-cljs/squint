(ns index
  (:require
   [my-component :as mc]
   ["react-dom/client" :as rdom]))

(def root (rdom/createRoot (js/document.getElementById "app")))
(.render root #jsx [mc/MyComponent])

