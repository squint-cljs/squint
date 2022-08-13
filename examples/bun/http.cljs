(ns bun.http)

(def default
  {:port 3000
   :fetch (fn [_req]
            (js/Response. "Welcome to Bun!"))})
