(ns ui)

;; Shared styles as Clojure maps: reusable and composable (assoc / merge per
;; widget). Each renderer takes a style map directly - #html and reagami
;; stringify it, preact sets it as a style object - so no conversion helper.
(def card
  {:font-family "system-ui, sans-serif"
   :border "1px solid #e2e2e2"
   :border-radius "10px"
   :padding "14px 16px"
   :margin "10px"
   :max-width "280px"
   :box-shadow "0 1px 2px rgba(0,0,0,.06)"})

(def label
  {:font "600 11px system-ui"
   :letter-spacing ".08em"
   :text-transform "uppercase"
   :margin-bottom "8px"})

(def counted {:font-size "14px" :margin-bottom "8px"})

(def btn
  {:font "14px system-ui"
   :padding "4px 10px"
   :border "1px solid #ccc"
   :border-radius "6px"
   :cursor "pointer"})
