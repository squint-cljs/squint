(ns index
  (:require [my-component :as MyComponent]
            ["react-dom/client" :refer [createRoot]]))

(def root (createRoot (js/document.getElementById "app")))
(.render root #jsx [MyComponent/MyComponent])
