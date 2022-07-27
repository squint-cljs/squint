(ns tasks
  (:require
   [babashka.tasks :refer [shell]]
   [clojure.string :as str]))

(defn watch-cljs [{:keys []}]
  (let [watch (requiring-resolve 'pod.babashka.fswatcher/watch)]
    (watch "."
           (fn [{:keys [type path]}]
             (when
                 (and (#{:write :write|chmod} type)
                      (str/ends-with? path ".cljs"))
               (shell "node node_modules/.bin/cherry" path)))
           {:recursive true})
    @(promise)))
