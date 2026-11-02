(require '["https://esm.sh/preact" :refer [h render]])

;; --- Hiccup -> Preact element converter ---
(defn hiccup->preact [x]
  (cond
    (or (string? x) (number? x)) x
    (vector? x)
    (let [[tag & args] x
          [attrs children] (if (map? (first args))
                             [(clj->js (first args)) (rest args)]
                             [{} args])]
      (if (fn? tag)
        (hiccup->preact (apply tag args))
        (h tag attrs (mapv hiccup->preact children))))

    :else
    (throw (js/Error. (str "Invalid hiccup: "x)))))

;; --- Application state ---
(def ohms-data (atom {:voltage 12 :current 0.5 :resistance 24}))


;; --- Pure calculation logic ---
(defn calc-ohms [{:keys [voltage current resistance] :as data}]
  (if (nil? voltage)
    (assoc data :voltage (* current resistance))
    (assoc data :current (/ voltage resistance))))


;; --- Slider component ---
(defn slider [the-atom calc-fn param value min max step invalidates]
  [:input {:type "range"
           :value value
           :min min
           :max max
           :step step
           :style {:width "50%"}
           :onInput (fn [e]
                      (let [new-value (js/parseFloat (.. e -target -value))]
                        (swap! the-atom
                          (fn [data]
                            (-> data
                              (assoc param new-value)
                              (dissoc invalidates)
                              calc-fn)))))}])


;; --- Page component ---
(defn ohms-law-page []
  (let [{:keys [voltage current resistance]} @ohms-data]
    [:div
     [:h3 "Ohm's Law Calculator"]

     [:div
      "Voltage: " (.toFixed voltage 2) "V"
      [slider ohms-data calc-ohms :voltage voltage 0 30 0.1 :current]]

     [:div
      "Current: " (.toFixed current 2) "A"
      [slider ohms-data calc-ohms :current current 0 3 0.01 :voltage]]

     [:div
      "Resistance: " (.toFixed resistance 2)
      [slider ohms-data calc-ohms :resistance resistance 0 100 1 :voltage]]]))


;; --- Root mounting ---
;; Squint Playground convention:
;; DOM always goes into the element with the id "app".
(def root (or (js/document.querySelector "#app")
              (doto (js/document.createElement "div")
                (set! -id "app")
                (js/document.body.prepend))))


(defn render! []
  (render (hiccup->preact [ohms-law-page]) root))


(add-watch ohms-data :render
           (fn [_ _ _ _] (render!)))


(render!)
