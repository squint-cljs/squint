(ns example
  (:require
   ["https://deno.land/std@0.146.0/http/server.ts" :as server]))

(def port 8080)

(defn handler [req]
  (let [agent (-> req (.-headers) (.get "user-agent"))
        body (str "Your user agent is: " (or agent
                                             "Unknown"))]
    (new js/Response body #js {:status 200})))

(server/serve handler #js {:port port})
