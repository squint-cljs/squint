(ns app
  (:require ["preact" :refer [render]]))

;; Preact via squint's :jsx-runtime (set in squint.edn). `#jsx` compiles to
;; jsx()/jsxs() calls that import preact's runtime directly, so there's no
;; separate JSX transform step - and it works at the REPL too (the compiled
;; output is plain JS, not raw <tags>).
(def card-style
  "font-family:system-ui,sans-serif;border:1px solid #e2e2e2;border-radius:10px;padding:14px 16px;margin:10px;max-width:280px;box-shadow:0 1px 2px rgba(0,0,0,.06)")

(defn App []
  #jsx [:div {:style card-style}
        [:div {:style "font:600 11px system-ui;letter-spacing:.08em;text-transform:uppercase;margin-bottom:8px;color:#673ab8"}
         "preact · #jsx"]
        [:div {:style "font-size:14px"} "preact: " [:strong "ok"]]])

(render #jsx [App] (js/document.querySelector "#preact"))
