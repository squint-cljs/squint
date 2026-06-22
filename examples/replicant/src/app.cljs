(ns app
  (:require [clojure.walk :as walk]
            [ohm]
            [replicant.dom :as d]))

;; A trimmed copy of replicant's own dev demo runner (dev/replicant/dev.cljs),
;; wired to a single example so it renders exactly like the upstream demo.

(defonce store (atom {}))

(def examples
  [ohm/example])

(defn get-example [k]
  (first (filter (comp #{k} :k) examples)))

(defn render [state]
  [:main
   (when-let [{:keys [f k]} (get-example (:example state))]
     [:div (f state k)])])

(defn interpolate [^js event args]
  (walk/postwalk
   (fn [x]
     (cond
       (= x :event.target/value)
       (.. event -target -value)

       (= x :event.target/selected-option-values)
       (map #(.-value %) (seq (.. event -target -selectedOptions)))

       :else
       x))
   args))

(defn dispatch-actions [{:replicant/keys [dom-event]} actions]
  (js/requestAnimationFrame
   #(doseq [[action & args] actions]
      (let [args (cond->> args
                   dom-event (interpolate dom-event))]
        (case action
          :actions/assoc-in
          (apply swap! store assoc-in args)

          (let [k (:example @store)]
            (if-let [impl (get-in (get-example k) [:actions action])]
              (dispatch-actions nil (apply impl (get @store k) args))
              (println "Unknown action" action))))))))

(defn start []
  (d/set-dispatch! dispatch-actions)
  (let [el (js/document.getElementById "app")]
    (add-watch store ::render (fn [_ _ _ state]
                                (d/render el (render state))))
    (let [{:keys [k initial-data]} (first examples)]
      (swap! store assoc :example k k initial-data))))

(start)
