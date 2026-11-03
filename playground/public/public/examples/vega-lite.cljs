(defn ^:async eval-script [url]
  (let [resp (js-await (js/fetch url))
        script (js-await (.text resp))]
    (js/eval.call js/globalThis script)))

(js-await (eval-script "https://cdn.jsdelivr.net/npm/vega@5"))
(js-await (eval-script "https://cdn.jsdelivr.net/npm/vega-lite@4"))
(js-await (eval-script "https://cdn.jsdelivr.net/npm/vega-embed@6"))

;; Squint Playground convention:
;; DOM always goes into the element with the id "app".
(def app (or (js/document.getElementById "app")
             (doto (js/document.createElement "div")
               (set! -id "app")
               (js/document.body.prepend))))

(def spec {:$schema "https://vega.github.io/schema/vega-lite/v4.0.json"
           ;; fill #app's width, leaving room for the actions menu
           :width "container"
           ;; fit the axes and labels inside that width too, not just the plot area
           :autosize {:type "fit" :contains "padding"}
           :description "A simple bar chart with embedded data."
           :data {:values [{:a "A",:b 28},{:a "B",:b 55},{:a "C",:b 43}
                           {:a "D",:b 91},{:a "E",:b 81},{:a "F",:b 53}
                           {:a "G",:b 19},{:a "H",:b 87},{:a "I",:b 52}]}
           :mark :bar
           :encoding {:x {"field" "a","type" "ordinal"}
                      :y {"field" "b","type" "quantitative"}}})

;; Embed into a child. vegaEmbed adds its own class and padding to the element it is given,
;; which should not be #app itself.
(def chart (doto (js/document.createElement "div")
             (->> (.append app))))

(js/vegaEmbed chart spec)
