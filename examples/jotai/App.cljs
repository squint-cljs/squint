(ns App
  #_:clj-kondo/ignore
  (:require ["react" :as React])
  (:require ["react-dom" :as rdom])
  (:require ["jotai" :as jotai :refer [useAtom]]))

(def !anime (jotai/atom
             [{:title "Ghost in the Shell"
               :year 1995
               :watched true}
              {:title "Serial Experiments Lain"
               :year 1998
               :watched false}]))

(defn App []
  (let [[anime setAnime] (useAtom !anime)]
    #jsx [:div
          [:ul
           (for [item anime]
             #jsx [:li {:key (:title item)}
                   (:title item)])]
          [:button {:onClick #(setAnime (conj anime
                                              {:title "Cowboy Bebop"
                                               :year 1998
                                               :watched false}))}
           "Add Cowboy Bebop"]]))

(defonce elt (js/document.querySelector "#app"))

(rdom/render #jsx [App] elt)
