(ns index
  (:require [another :as a]))

(def card-style
  "font-family:system-ui,sans-serif;border:1px solid #e2e2e2;border-radius:10px;padding:14px 16px;margin:10px;max-width:280px;box-shadow:0 1px 2px rgba(0,0,0,.06)")

(def label-style
  "font:600 11px system-ui;letter-spacing:.08em;text-transform:uppercase;margin-bottom:8px")

;; Cross-ns demo: render a var defined in the `another` namespace. Redefining
;; another/s at the REPL (or editing another.cljs) updates this on re-render,
;; because the alias derefs the live globalThis.another cell.
(defn hello []
  #html [:div {:style card-style}
         [:div {:style (str label-style ";color:#0a7d5a")} "plain squint · #html"]
         [:pre {:style "margin:0;font-size:14px"} "another/s = " a/s]])

(set! (.-innerHTML
       (js/window.document.querySelector "#app")) (hello))
