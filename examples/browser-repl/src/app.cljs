(ns app
  (:require ["preact" :refer [render]]
            ["preact/hooks" :refer [useState]]
            [ui]))

;; Preact via squint's :jsx-runtime (set in squint.edn). `#jsx` compiles to
;; jsx()/jsxs() calls that import preact's runtime directly, so there's no
;; separate JSX transform step, and it works at the REPL too (the compiled
;; output is plain JS, not raw <tags>). useState drives re-render automatically;
;; the framework owns reactivity (no manual watch).
(defn App []
  (let [[n set-n] (useState 0)]
    #jsx [:div {:style (ui/css ui/card)}
          [:div {:style (ui/css (assoc ui/label :color "#673ab8"))} "preact · #jsx"]
          [:div {:style (ui/css ui/counted)} "Counted: " n]
          [:button {:style (ui/css ui/btn) :onClick #(set-n (inc n))} "Click me!"]]))

(render #jsx [App] (js/document.querySelector "#preact"))
