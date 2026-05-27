(ns index
  (:require [another :as a]))

;; Cross-ns demo: render a var defined in the `another` namespace. Redefining
;; another/s at the REPL (or editing another.cljs) updates this on re-render,
;; because the alias derefs the live globalThis.another cell.
(defn hello []
  #html [:pre "another/s = " a/s])

(set! (.-innerHTML
       (js/window.document.querySelector "#app")) (hello))
