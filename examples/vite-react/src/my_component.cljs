(ns my-component
  (:require ["react" :refer [useState]]))

(defonce MINE 0)

(defn MyComponent []
  (let [[state setState] (useState 0)]
    #jsx [:div "You clicked " state "times"
          [:button {:onClick #(do
                                (set! MINE (inc MINE))
                                (setState (inc state)))}
           "Click me"]]))
