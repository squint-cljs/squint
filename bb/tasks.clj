(ns tasks
  (:require
   [babashka.fs :as fs]
   [babashka.process :refer [shell]]
   [clojure.edn :as edn]
   [clojure.java.io :as io]))
(defn shadow-extra-config
  []
  (let [core-config (edn/read-string (slurp (io/resource "cherry/cljs.core.edn")))
        vars (:vars core-config)
        ks (map #(symbol (munge %)) vars)
        vs (map #(symbol "cljs.core" (str %)) vars)
        core-map (zipmap ks vs)]
    {:modules
     {:cljs_core {:exports core-map}}}))

(defn build-cherry-npm-package []
  (fs/create-dirs ".work")
  (spit ".work/config-merge.edn" (shadow-extra-config))
  (shell "npx shadow-cljs --config-merge .work/config-merge.edn release cherry"))

(defn watch-cherry []
  (fs/create-dirs ".work")
  (spit ".work/config-merge.edn" (shadow-extra-config))
  (shell "npx shadow-cljs --config-merge .work/config-merge.edn watch cherry"))
