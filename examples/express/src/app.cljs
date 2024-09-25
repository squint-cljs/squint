(ns app
  (:require ["express$default" :as express]))

(def app (express))

(app.get "/" (fn [req res]
               (res.send
                #html [:html
                       [:body "Hello ", (or (:name req.query)
                                            "unknown")]
                       [:ul
                        [:li 1]
                        [:li 2]
                        [:li 3]
                        (map (fn [i]
                               #html [:li (inc i)]) [3 4 5])]])))

(app.listen 1337)
