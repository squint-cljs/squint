(ns ohm)

(defn slider [param value min max step invalidates]
  [:input {:type "range"
           :value value
           :min min
           :max max
           :step step
           :style {:width "50%"}
           :on {:input [[:actions/calculate-ohm param :event.target/value invalidates]]}}])

(defn render [state k]
  (let [{:keys [voltage current resistance]} (get state k)]
    [:div
     [:h3 "Ohm's Law Calculator"]
     [:div
      "Voltage: " (.toFixed voltage 2) "V"
      (slider :voltage voltage 0 30 0.1 :current)]
     [:div
      "Current: " (.toFixed current 2) "A"
      (slider :current current 0 3 0.01 :voltage)]
     [:div
      "Resistance: " (.toFixed resistance 2) ""
      (slider :resistance resistance 0 100 1 :voltage)]]))

(defn calc-ohms [{:keys [voltage current resistance] :as data}]
  (if (nil? voltage)
    (assoc data :voltage (* current resistance))
    (assoc data :current (/ voltage resistance))))

(def example
  {:title "Ohm's law"
   :k :ohm
   :f render
   :initial-data {:voltage 12
                  :current 0.5
                  :resistance 24}
   :actions {:actions/calculate-ohm
             (fn [data param val invalidates]
               [[:actions/assoc-in [:ohm]
                 (calc-ohms
                  (-> data
                      (assoc param (js/parseFloat val))
                      (dissoc invalidates)))]])}})
