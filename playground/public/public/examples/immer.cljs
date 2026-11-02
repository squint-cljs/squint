(require '["https://esm.sh/immer@10.0.3" :as immer])

;; Squint Playground convention:
;; DOM always goes into the element with the id "app".
(def app (or (js/document.getElementById "app")
             (doto (js/document.createElement "div")
               (set! -id "app")
               (js/document.body.prepend))))

(defn append [str]
  (.appendChild app
   (doto (js/document.createElement "div")
     (set! -innerText (js/JSON.stringify str)))))

(def base-state [{:title "Learn Squint!"
                  :done true}
                 {:title "Try Immer"
                  :done false}])

(def next-state (immer/produce base-state
                  (fn [draft]
                    (assoc-in! draft [0 :done] true)
                    (conj! draft {:title "Tweet about it"}))))

(append base-state)
(append next-state)
