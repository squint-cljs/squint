(ns index)

(defn hello []
  #html [:pre "Hello"])

(set! (.-innerHTML
       (js/window.document.querySelector "#app")) (hello))
