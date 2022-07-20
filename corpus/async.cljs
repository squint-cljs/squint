(ns async)

(defn ^:async status []
  (let [resp (js/await (js/fetch "https://clojure.org"))
        status (js/await (.-status resp))]
    status))

(def chalk (.-default (js/await (js/import "chalk"))))

(js/console.log "status:" (chalk.green (js/await (status))))
