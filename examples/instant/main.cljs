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

(defn Names [data]
  (let [names data.names]
    #jsx [:<>
          (map (fn [n]
                 #jsx [:div "Name: " n])
               names)]))

(defn App []
  (let [[isLoading error data] (.useQuery db {:names {}})]
    #jsx [:div
           [:p "Add name"]
          [:form
           {:onSubmit (fn [e]
                        (.preventDefault e)
                        (let [n (-> e .-target first)]
                          (add-name n.value)
                          (set! n.value "")))}
           [:input]]
          (js/console.log data)
          [:div [Names data]]]))

(def root (rdom/createRoot (js/document.getElementById "app")))
(.render root #jsx [App])
1
