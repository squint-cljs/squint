(ns defclass-test
  (:require [squint.core :refer [defclass]]))

(defclass class-1
  (field _x)
  (field __secret :dude)
  (constructor [this x-arg] (set! _x x-arg))

  Object
  ;; tests munging of method names
  (get-name-separator [_] "-")
  )

(defclass Class2
  (extends class-1)
  (field _y 1)
  (constructor [th-is x y-z]
               (super (+ x y-z))
               th-is
               )

  Object
  (dude [t-his] t-his ;; use this arg
        (str _y (.get-name-separator (js* "super")))))

(def c (new Class2 1 2))

(prn [c (.dude c)])
