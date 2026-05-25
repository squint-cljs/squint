(ns index
  (:require ["squint-cljs/core.js" :as squint_core]
            ["joi" :as joi]))

(defn hello []
  #html [:pre "Hello"])

(set! (.-innerHTML
       (js/window.document.querySelector "#app")) (hello))

