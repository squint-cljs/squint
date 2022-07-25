(ns index
  (:require
   ["https://cdn.skypack.dev/canvas-confetti$default" :as confetti]
   ["https://cdn.skypack.dev/react" :as react :refer [useEffect]]
   ["https://cdn.skypack.dev/react-dom" :as rdom]))

(defn $
  "Out of lack of varargs in cherry right now, we pass children as a vector"
  [elt props children]
  (let [props (clj->js props)
        elt (if (keyword? elt)
              (name elt)
              elt)]
    (if (vector? children)
      (apply react/createElement elt props children)
      (react/createElement elt props children))))

(defn App []
  (useEffect (fn [] (confetti)) #js [])
  (let [[count setCount] (react/useState 0)]
    ($ :div nil
       [($ :p nil ["You clicked " count " times!"])
        ($ :button {:onClick (fn []
                               (confetti)
                               (setCount (inc count)))}
           "Click me")])))

(rdom/render
 ($ App nil [])
 (js/document.getElementById "app"))
