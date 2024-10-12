(ns main
  (:require ["@instantdb/react" :refer [init tx id]]
            ["react-dom/client" :as rdom]
            ["react" :as React]))

(def APP_ID js/import.meta.env.VITE_APP_ID)

(def db (init {:appId APP_ID}))

(defn ^:async add-name [n]
  (let [v (js-await
           (.transact db
                      (-> tx.names (aget (id))
                          (.update {:name n
                                    :createdAt (js/Date.now)}))))]
    (js/console.log :v v)))

(defn Names [{:keys [names] :as all}]
  #jsx [:<>
        (map (fn [{:keys [name]}]
               (js/console.log :name name)
               #jsx [:div "Name: " name])
             names)])

(defn App []
  (let [{:keys [isLoading error data]} (.useQuery db {:names {}})]
    #jsx [:div
           [:p "Add name"]
          [:form
           {:onSubmit (fn [e]
                        (.preventDefault e)
                        (let [n (-> e .-target first)]
                          (add-name n.value)
                          (set! n.value "")))}
           [:input]]
          (when-not isLoading
            #jsx [:div
                  [Names {:names data.names}]])]))

(def root (rdom/createRoot (js/document.getElementById "app")))
(.render root #jsx [App])
2
