(ns index
  (:require
   ["https://cdn.skypack.dev/canvas-confetti$default" :as confetti]
   ["https://cdn.skypack.dev/react" :as react :refer [useEffect]]
   ["https://cdn.skypack.dev/react-dom" :as rdom]))

(defn $
  "Render element (keyword or symbol) with optional props"
  [elt props & children]
  (let [[props children]
        (if (map? props)
          [(clj->js props) children]
          [nil (cons props children)])
        elt (if (keyword? elt)
              (name elt)
              elt)]
    (react/createElement elt props (into-array children))))

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
 ($ App nil [])
 (js/document.getElementById "app"))
