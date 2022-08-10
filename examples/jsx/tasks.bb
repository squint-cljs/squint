(ns tasks
  (:require
   [babashka.tasks :refer [shell]]
   [clojure.string :as str]))

(defn watch-cljs [{:keys []}]
  (let [watch (requiring-resolve 'pod.babashka.fswatcher/watch)]
    (watch "pages"
           (fn [{:keys [type path]}]
             (when
                 (and (#{:write :write|chmod} type)
                      (str/ends-with? path ".cljs"))
               (shell {:continue true} "node node_modules/.bin/cherry" path))))
    @(promise)))
