(require '["https://esm.sh/idiomorph" :refer [Idiomorph] :rename {Idiomorph I}])

;; Squint Playground convention:
;; DOM always goes into the element with the id "app".
(def root (or
            (js/document.querySelector "#app")
            (doto (js/document.createElement "div")
              (set! -id "app")
              (js/document.body.prepend))))

(def state (atom {:counter 0
                  :input "Hello"
                  :show true}))

(def data-registry {})

(defn reg-event-handler! [id data]
  (assoc! data-registry id data)
  id)

(reg-event-handler! :my-component/click
  #(swap! state update :counter inc))

(reg-event-handler! :my-component/input
  #(swap! state assoc :input
     (-> % :target :value)))

(defn my-component []
  #html [:<>
         [:div "Count: " (:counter @state)]
         [:div [:button {:data-click :my-component/click}
                "Please click me"]]
         [:input {:data-input :my-component/input
                  :type :text :value (:input @state)}]
         [:pre (:input @state)]])

(reg-event-handler! :show-btn-click
  #(swap! state update :show not))

(defn ui []
  #html [:div
         [:button
          {:data-click :show-btn-click}
          "Show?"]
         (when (:show @state)
           (my-component))])

(def node-pool (js/Map.))

(defn restore-event-handlers []
  (doseq [event [:click :input]]
    (let [nodes (.querySelectorAll root (str "[data-" event "]"))]
      (doseq [node nodes]
        (when-not (contains? node-pool node)
          (assoc! node-pool node
            (.addEventListener node event
              (get data-registry (.getAttribute node (str "data-" event))))))))))

(defn morph []
  (I/morph root (str (ui))
    {:morphStyle :innerHTML
     :callbacks {:afterNodeRemoved (fn [node]
                                     (dissoc! node-pool node))}})
  (restore-event-handlers))

(add-watch state :state (fn [_ _ _ _]
                          (morph)))

(morph)
