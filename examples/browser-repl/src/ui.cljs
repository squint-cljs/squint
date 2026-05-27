(ns ui
  (:require [clojure.string :as str]))

;; Styles as Clojure maps: reusable and composable (merge / assoc per widget).
;; `css` renders a map to an inline-style string, which all three renderers
;; accept (preact via cssText, #html as the attr, reagami as a style string) -
;; a plain style map otherwise means three different formats.
;; (str/join exercises clojure.string at the REPL/in the browser.) Map keys are
;; strings once compiled, so no `name` needed.
(defn css [m]
  (str/join ";" (map (fn [[k v]] (str k ":" v)) m)))

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
