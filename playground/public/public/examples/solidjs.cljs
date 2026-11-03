(require '["https://esm.sh/solid-js@1.8.10" :as solid])
(require '["https://esm.sh/solid-js@1.8.10/web" :as solid-web])

(defn ChildComponent [{:keys [count]}]
  #jsx [:div "Count value is " count])

(defn CountingComponent []
  (let [[count setCount] (solid/createSignal 10)
        interval (js/setInterval #(setCount inc)
                   1000)]
    (solid/onCleanup #(js/clearInterval interval))
    #jsx [ChildComponent {:count
                          ;; reactive value must be wrapped in thunk
                          ;; when using jsx-runtime instead of solid-js compiler
                          (fn [] count)}]))

;; Squint Playground convention:
;; DOM always goes into the element with the id "app".
(def elt (or
           (js/document.querySelector "#app")
           (doto (js/document.createElement "div")
             (set! -id "app")
             (js/document.body.prepend))))

(defonce dispose (atom (fn [])))
(do
  (@dispose)
  (reset! dispose
    (solid-web/render (fn [] #jsx [CountingComponent]) elt)))
