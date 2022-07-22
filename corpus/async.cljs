(ns async)

(defn ^:async status []
  (let [resp (js/await (js/fetch "https://clojure.org"))
        status (js/await (.-status resp))]
    status))

(js/console.log "status:" (js/await (status)))
