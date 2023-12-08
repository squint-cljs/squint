(ns App
  (:require ["react" :as r :refer [useState]]
            ["./App.css"]
            ;; because image assets are imported as modules,
            ;; we need to use $default to get the actual image url
            ;; ---
            ;; vite.svg is in the public folder, use absolute path
            ["/vite.svg$default" :as viteLogo]
            ;; react.svg is in the assets folder, use relative path
            ["./assets/react.svg$default" :as reactLogo]
            ;; bb.svg is in the assets folder, use relative path
            ["./assets/bb.svg$default" :as bbLogo]))

(defn Main []
  (let [[count setCount] (useState 0)]
    #jsx [r/Fragment
          [:div
           [:a {:href "https://github.com/squint-cljs/squint" :target "_blank"}
            [:img {:src bbLogo :className "logo bb" :alt "bb logo"}]]
           [:a {:href "https://vitejs.dev" :target "_blank"}
            [:img {:src viteLogo :className "logo" :alt "Vite logo"}]]
           [:a {:href "https://react.dev" :target "_blank"}
            [:img {:src reactLogo :className "logo react" :alt "React logo"}]]]
          [:h1 "Squint + Vite + React"]
          [:div {:className "card"}
           [:button {:onClick (fn [[_ _ _]]
                                (setCount (inc count)))}
            "count is " count]
           [:p "Edit "
            [:code "src-cljs/App.cljs"]
            " and save to test HMR"]]
          [:p {:className "read-the-docs"}
           "Click on the Babashka, Vite and React logos to learn more"]]))
