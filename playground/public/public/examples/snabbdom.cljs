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
    (let [[tag attrs & children] hiccup
          [attrs children] (if (map? attrs)
                             [attrs children]
                             [nil (cons attrs children)])
          el (h tag attrs
               (mapv create-node children))]
      el)
    :else
    (throw (js/Error. (str "Invalid hiccup: " hiccup)))))

(def state (atom {:counter 0
                  :input "Hello"}))

(defn my-component []
  [:div
   [:div (pr-str @state)]
   [:div "Count: " (:counter @state)]
   [:div [:button {:on {:click #(do
                                  (swap! state update :counter inc))}}
          "Please click me"]]
   [:input {:on {:input #(swap! state assoc :input (-> % :target :value))}
            :style {:background :lightblue}
            :props {:type :text :value (:input @state)}}]
   [:pre {:style {:color "#3cb371"
                :font-family "inherit"
                :font-size "inherit"}}
    (:input @state)]])

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

(def vdom (atom (patch root (create-node (my-component)))))

(defn morph []
  (let [new (create-node (my-component))]
    (patch @vdom new)
    (reset! vdom new)))

(add-watch state :state (fn [_ _ _ _]
                          (morph)))
