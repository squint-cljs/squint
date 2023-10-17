(ns MyComponent
  (:require ["react" :refer [useState]]))

(defn MyComponent []
  (let [[state setState] (useState 0)]
    #jsx [:div "You xxxxxxxxxxxclicked " state "times"
          [:button {:onClick #(setState (inc state))}
           "Click me"]]))
