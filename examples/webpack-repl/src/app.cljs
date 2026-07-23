(ns app
  (:require ["preact" :as p]))

;; Expose the page's preact instance so the REPL can prove instance sharing
;; (a REPL require of "preact" must resolve to THIS module, via the manifest
;; registry, not a second copy).
(set! js/globalThis.__page_preact p)

(defn hot-fn
  "Redefined by the e2e :hot :ws test."
  []
  1)

(defn render! []
  (set! (.-textContent (js/document.getElementById "app"))
        "hello from squint webpack repl"))

(render!)
