(ns index
  (:require ["joi" :as joi]
            [myapp :as m]))

(prn (m/foobar))

(defn hello []
  #html [:pre "xDude"])

(set! (.-innerHTML
       (js/window.document.querySelector "#app")) (hello))

