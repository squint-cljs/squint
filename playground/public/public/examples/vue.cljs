(require '["https://cdn.jsdelivr.net/npm/vue@3.3.10/dist/vue.esm-browser.js" :as vue])
;; Squint Playground convention:
;; DOM always goes into the element with the id "app".
(def app (or (js/document.getElementById "app")
             (doto (js/document.createElement "div")
               (set! -id "app")
               (js/document.body.prepend))))

;; Vue reads the template from the element it mounts on
(set! (.-innerHTML app) "<button @click=\"count++\">
  Count is: {{ count }}
</button>")

(def vue-app (vue/createApp
           {:setup (fn []
                     {:count (vue/ref 0)})}))

(.mount vue-app app)
