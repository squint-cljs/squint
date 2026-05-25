(ns index
  (:require ["joi" :as joi]
            [myapp :as m]))

(prn (m/foobar))

(defn hello []
  #html [:pre "Dude"])

(set! (.-innerHTML
       (js/window.document.querySelector "#app")) (hello))

