(ns index
  (:require
   ["https://cdn.skypack.dev/canvas-confetti$default" :as confetti]
   ["https://cdn.skypack.dev/react" :as react :refer [useEffect]]
   ["https://cdn.skypack.dev/react-dom" :as rdom])
  (:require-macros ["./macros.mjs" :refer [$]]))

(defn App []
  (useEffect (fn [] (confetti)) #js [])
  (let [[count setCount] (react/useState 0)]
    ($ :div
       ($ :p "You clicked " count " times!")
       ($ :button {:onClick (fn []
                              (confetti)
                              (setCount (inc count)))}
          "Click me"))))

(rdom/render
 ($ App)
 (js/document.getElementById "app"))
