(ns main
  {:clj-kondo/config '{:linters {:unresolved-symbol {:exclude [js-await]}}}}
  (:require ["@instantdb/react" :refer [init tx id]]
            ["react-dom/client" :as rdom]
            #_:clj-kondo/ignore
            ["react" :as React]
            [clojure.string :as str]))

(def APP_ID js/import.meta.env.VITE_APP_ID)

(def db (init {:appId APP_ID}))

(defn ^:async add-entry [{:keys [name message]}]
  (.transact db
             (-> tx.guestbookEntries (aget (id))
                 (.update {:name name
                           :message message
                           :createdAt (js/Date.now)}))))

(defn Entries [{:keys [entries]}]
  #jsx [:div {:style styles.entries}
        (map (fn [{:keys [name message id]}]
               #jsx [:div {:style styles.entry
                           :key id}
                     [:div {:style styles.entryText}
                      name]
                     [:div {:style styles.entryText}
                      message]])
             entries)])

(declare styles)

(defn Form [_]
  #jsx
  [:div {:style styles.form}
   [:form
    {:onSubmit (fn [e]
                 (.preventDefault e)
                 (let [t (-> e .-target)
                       children (-> t .-children)
                       inputs (take 2 children)
                       [name message] (map #(.-value %) inputs)]
                   (when (and (not (str/blank? name))
                              (not (str/blank? message)))
                     (add-entry {:name name
                                 :message message})
                     (doseq [i inputs]
                       (set! i.value "")))))}
    [:input {:style styles.input
             :autoFocus true
             :placeholder "Your name"
             :type "text"}]
    [:input {:style styles.input
             :placeholder "Your message"
             :type "text"}]
    [:button {:action "submit"}
     "Send"]]])

(defn App []
  (let [{:keys [isLoading error data]} (.useQuery db {:guestbookEntries {}})]
    #jsx [:div {:style styles.container}
          [:div {:style styles.header} "Guestbook"]
          [Form]
          (cond
            error
            #jsx [:div error]
            (not isLoading)
            #jsx [:div
                  [Entries {:entries data.guestbookEntries}]])]))

(def root (rdom/createRoot (js/document.getElementById "app")))
(.render root #jsx [App])

(def styles
  {:container {:boxSizing "border-box"
               :backgroundColor "#fafafa"
               :fontFamily "code, monospace"
               :height "100vh"
               :display "flex"
               :justifyContent "center"
               :alignItems "center"
               :flexDirection "column"}
   :header {:letterSpacing "2px"
            :fontSize "50px"
            :color "lightgray"
            :marginBottom "10px"}
   :form {:boxSizing "inherit"
          :display "flex"
          :border "1px solid lightgray"
          :borderBottomWidth "0px"
          :width "350px"}
   :input {:backgroundColor "transparent"
           :fontFamily "code, monospace"
           :width "287px"
           :padding "10px"
           :fontStyle "italic"
           :display :block}
   :entries {:boxSizing "inherit"
             :width "350px"}
   :entry {:display "flex"
          :alignItems "center"
          :padding "10px"
          :border "1px solid lightgray"
          :borderBottomWidth "0px"}
   :entryText {:flexGrow 1
               :overflow "hidden"}
   :footer {:margin-top "20px"
            :fontSize "10px"}})
