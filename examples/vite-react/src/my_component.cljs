(ns my-component
  (:require ["react" :refer [useState]]))

;; this needs to be private since non-component exports break vite react HMR
(defonce ^:private x 10)

(defn MyComponent []
  (let [[state setState] (useState 0)]
    #jsx [:div "You clicked " state " times"
          [:button {:onClick #(do
                                (set! x (inc x))
                                (setState (inc state)))}
           "Click me"]]))
