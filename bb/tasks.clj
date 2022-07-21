(ns tasks
  (:require [babashka.process :refer [shell]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def core-config )

(def core-vars (:vars core-config))

(def core->js (:to-js core-config))


(defn shadow-extra-config []
  (let [core-config (edn/read-string (slurp (io/resource "cherry/cljs.core.edn")))
        vars (:vars core-config)
        to-js (:to-js core-config)
        _ (prn to-js)
        ks (map #(get to-js % %) vars)
        vs (map #(symbol "cljs.core" (str %)) vars)
        core-map (zipmap ks vs)]
    (prn core-map)
    {:modules
     {:cljs_core {:exports core-map}}}))

(defn build-cherry-npm-package []
  (shell "npx shadow-cljs release cherry --config-merge"
         (shadow-extra-config)))
