(ns test
  (:require ["react-dom/server" :refer [renderToStaticMarkup]]))

(defn App []
  (let [x "dude"]
    #jsx [:a {:href x} "Hello"
          (+ 1 2 3) x
          (if (odd? 2)
            #jsx [:div "Yo"]
            #jsx [:div "Fo"])
          [:pre [:code "(+ 1 2 3)"]]]))


(println (js/await (renderToStaticMarkup #jsx [App])))
