(ns myelement
  (:require [squint.core :refer [defclass]]))

(defclass MyElement
  (extends js/HTMLElement)

  (field -shadow)
  (field count 0)

  (constructor [this]
    (super)
    (set! -shadow (.attachShadow this {:mode :open})))

  Object
  (handleClick [this e]
    (set! count (inc count))
    (.render this))

  (connectedCallback [this]
    (.addEventListener this "click" this.handleClick)
    (.render this))

  (disconnectedCallback [this]
    (.removeEventListener this "click" this.handleClick))

  (render [this]
    (set! (.-innerHTML -shadow)
      #html [:button "Click count " count])))

(.define customElements :my-element MyElement)

;; Squint Playground convention:
;; DOM always goes into the element with the id "app".
(def app (or (js/document.querySelector "#app")
           (doto (js/document.createElement :div)
             (set! -id :app)
             (js/document.body.prepend))))

(set! (.-innerHTML app) #html [:my-element])
