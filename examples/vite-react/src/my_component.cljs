(ns my-component
  (:require ["react" :refer [useState]]))

(defn adder [& n]
  (apply + n))

(defn MyComponent []
  (let [[state setState] (useState 0)]
    #jsx [:div "You clicked " state " times"
          [:button {:onClick #(setState (inc state))}
           "Click me"]]))
