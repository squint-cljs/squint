(ns server
  (:require
   ["dotenv" :as dotenv]
   ["http" :as http]
   ["url" :as url]
   ["pg$default" :as pg]))

(.config dotenv)
(def Pool pg.Pool)

(defonce pool
  (Pool.
   {:host (.. js/process -env -DB_HOST)
    :port (.. js/process -env -DB_PORT)
    :database (.. js/process -env -DB_NAME)
    :user (.. js/process -env -DB_USER) 
    :password (.. js/process -env -DB_PASS)}))

(defn ^:async execute-query [query params]
  (.-rows (js-await (.query pool query params))))

(def favicon
  (str
   "data:image/x-icon;base64,AAABAAEAEBAAAAAAAABoBQAAFgAAACgAAAAQAAAAIAAAAAEACAAAAAAAAAEAAAAAAAAAAAAAAAE"
   (apply str (take 1714 (repeat "A")))
   "P//AAD8fwAA/H8AAPxjAAD/4wAA/+MAAMY/AADGPwAAxjEAAP/xAAD/8QAA4x8AAOMfAADjHwAA//8AAP//AAA="))

(defn ^:async home-page [_req]
  (.then
   (execute-query "select * from users" [])
   (fn [users]
     (str
      "<!DOCTYPE html>\n"
      #html
       [:html
        [:head
         [:link {:href favicon :rel "icon" :type "image/x-icon"}]
         [:meta {:charset "UTF-8"}]
         [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
         [:title "Welcome to Squint"]
         [:link {:href "https://cdn.jsdelivr.net/npm/bulma@1.0.0/css/bulma.min.css" :rel "stylesheet"}]]
        [:body
         [:section.section
          [:div.content
           [:h2 [:b "Welcome to Squint"]]
           [:ul
            (doall
             (for [{:keys [first_name last_name]} users]
               #html [:li last_name "," first_name]))]]]]]))))

(defn ^:async search-handler [{:keys [url]}]
  (let [id (-> (url/parse url true) (.-query) :id)]
    (.then
     (execute-query "select * from users where id = $1" [id])
     (fn [users]
       (js/JSON.stringify #js {:users users})))))

(defn respond [req res {:keys [handler content-type status]}]
  (-> (handler req)
      (.then (fn [body]
               (set! (.-statusCode res) status)
               (.setHeader res "Content-Type" content-type)
               (.end res body)))
      (.catch (fn [error]
                (js/console.error error)
                (set! (.-statusCode res) 500)
                (.setHeader res "Content-Type" "text/plain")
                (.end res "an error occurred serving the request")))))

(defn handler [{:keys [url] :as req} res]
  (let [parsed-url (url/parse url true)
        path (.-pathname parsed-url)
        request-method (.-method req)]
    (respond
     req res
     (cond
       (and (= "GET" request-method) (= "/" path)) 
       {:handler home-page
        :status 200
        :content-type "text/html; charset=utf-8"}
       (and (= "GET" request-method) (.startsWith path "/search"))
       {:handler search-handler
        :status 200
        :content-type "application/json; charset=utf-8"}
       :else
       {:handler (^:async fn []
                             (js/Promise.resolve
                              (str "requested route " request-method " " path " not found")))
        :status 404
        :content-type "text/plain"}))))

(defn start-server [host port]
  (let [server (http/createServer handler)]
    (.listen server port host (fn [] (println "server started on" host port )))))

(start-server (.. js/process -env -HTTP_HOST) (.. js/process -env -HTTP_PORT))