(require '["https://esm.sh/snabbdom" :refer [h
                                             init
                                             classModule
                                             propsModule
                                             styleModule
                                             eventListenersModule]])

(def patch (init [classModule propsModule styleModule eventListenersModule]))

(defn create-node [hiccup]
  (cond
    (or
      (number? hiccup)
      (string? hiccup)) hiccup
    (vector? hiccup)
    (let [[tag & [attrs & children :as all-children]] hiccup
          [attrs children] (if (map? attrs)
                             [attrs children]
                             [nil all-children])
          el (if (fn? tag)
               (create-node (apply tag all-children))
               (do
                 (h tag attrs
                   (mapv create-node children))))]
      el)
    :else
    (do
      (throw (js/Error. (str "Invalid hiccup: " hiccup))))))

(defn slider [the-atom calc-fn param value min max step invalidates]
  [:input {:props {:type "range" :value value :min min :max max :step step}
           :style {:width "50%"}
           :on {:input (fn [e]
                         (let [new-value (js/parseFloat
                                           (aget (aget e "target") "value"))]
                           (swap! the-atom
                             (fn [data]
                               (-> data
                                 (assoc param new-value)
                                 (dissoc invalidates)
                                 calc-fn)))))}}])

(defn calc-ohms [{:keys [voltage current resistance] :as data}]
  (if (nil? voltage)
    (assoc data :voltage (* current resistance))
    (assoc data :current (/ voltage resistance))))

(def ohms-data (atom {:voltage 12 :current 0.5 :resistance 24}))

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
      "Resistance: " (.toFixed resistance 2) ""
      [slider ohms-data calc-ohms :resistance resistance 0 100 1 :voltage]]]))

;; Squint Playground convention:
;; DOM always goes into the element with the id "app".
(def app (or (js/document.getElementById "app")
             (doto (js/document.createElement "div")
               (set! -id "app")
               (js/document.body.prepend))))

;; snabbdom's patch replaces the node it is given, so mount on a child of #app
(def root (let [el (js/document.createElement "div")]
            (.replaceChildren app el)
            el))

(def vdom (atom (patch root (create-node [ohms-law-page]))))

(defn morph []
  (let [new (create-node [ohms-law-page])]
    (patch @vdom new)
    (reset! vdom new)))

(add-watch ohms-data :state (fn [_ _ _ _]
                              (morph)))
