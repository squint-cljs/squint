(ns App
  (:require ["react-native" :refer [Text View StyleSheet StatusBar]]
            [MyComponent :as MyComponent]))

(def styles (StyleSheet.create
             {:container  {:flex 1
                           :backgroundColor "#fff"
                           :alignItems "center"
                           :justifyContent "center"}}))

(defn- App []
  #jsx [:View {:style styles.container}
        [:Text "Open up App.js to start working on your app"]
        [MyComponent/MyComponent]
        [:StatusBar {:style "auto"}]])

(def default App)
