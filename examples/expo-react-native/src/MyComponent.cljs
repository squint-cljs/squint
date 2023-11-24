(ns MyComponent
  (:require ["react" :refer [useState]]
            ["react-native" :refer [Button Text View]]))

(defn MyComponent []
  (let [[state setState] (useState 0)]
    #jsx [View
          [Text "You clicked " state " times! :)"]
          [Button {:color "blue"
                   :onPress (fn [_]
                              (setState (inc state)))
                   :title "Click me"}]]))
