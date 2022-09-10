(ns App
  (:require ["./App.module.css$default" :as styles]
            ["./logo.svg$default" :as logo]
            ["solid-js" :refer [createSignal]]
            [squint.string :as str]))

(defn Counter []
  (let [[counter setCount] (createSignal 0)]
    #jsx [:div
          "Count:" (str/join " " (range (counter)))
          [:div
           [:button
            {:onClick (fn []
                        (setCount (inc (counter))))}
            "Click me"]]]))

(defn App []
  #jsx [:div {:class styles.App}
        [:header {:class styles.header}
         [:img {:src logo
                :class styles.logo
                :alt "logo"}]
         [Counter]]])

(def default App)
