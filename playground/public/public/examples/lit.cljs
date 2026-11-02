(ns mylit
  (:require [squint.core :refer [defclass js-template]]
   ["https://esm.sh/lit@3.0.0" :as lit]))

(defclass MyElement
  (extends lit/LitElement)
  (^:static field properties {:count {}})

  (constructor [this]
    (super)
    (set! this.count 0)
    (set! this.name "Hello"))

  Object
  (render [this]
    #html ^lit/html
    [:div
     [:h1 this.name]
     [:button {"@click" this.onClick
               :part "button"}
      "Click count " this.count]])

  (onClick [this]
    (set! this.count (inc this.count))))

(defonce foo
  (do
    (js/window.customElements.define "my-element" MyElement)
    true))

;; Squint Playground convention:
;; DOM always goes into the element with the id "app".
(def app (or (js/document.querySelector "#app")
           (doto (js/document.createElement "div")
             (set! -id "app")
             (js/document.body.prepend))))

(set! (.-innerHTML app) #html [:div
                               [:my-element]
                               #_[:my-element]])
