(require '["https://cdnjs.cloudflare.com/ajax/libs/mithril/2.2.2/mithril.min.js"])

;; js/globalThis.m, not js/m: (def m js/m) compiles to `var m = m`,
;; which reads the new, still empty var instead of the global
(def m js/globalThis.m)

;; Squint Playground convention:
;; DOM always goes into the element with the id "app".
(def root (or
            (js/document.querySelector "#app")
            (doto (js/document.createElement "div")
              (set! -id "app")
              (js/document.body.prepend))))

(def counter (atom 0))

(.mount m root
  {:view (fn []
           (m :main
             [(m :h1 "Try me out")
              (m :button
                {:onclick #(swap! counter inc)}
                "Clicks: " @counter)]))})
