(ns app
  (:require ["preact" :refer [render]]))

;; Preact via squint's :jsx-runtime (set in squint.edn). `#jsx` compiles to
;; jsx()/jsxs() calls that import preact's runtime directly, so there's no
;; separate JSX transform step - and it works at the REPL too (the compiled
;; output is plain JS, not raw <tags>).
(defn App []
  #jsx [:div "preact: " [:strong "ok"]])

(render #jsx [App] (js/document.querySelector "#preact"))
