(require '["https://esm.sh/preact@10.19.2" :as react])
(require '["https://esm.sh/preact@10.19.2/hooks" :as hooks])

(defn Counter []
  (let [[counter setCounter] (hooks/useState 0)]
    #jsx [:<>
          [:div "Counter " counter]
          [:button {:onClick #(setCounter (dec counter))} "-"]
          [:button {:onClick #(setCounter (inc counter))} "+"]]))

(defonce el (js/document.getElementById "cljs"))

(react/render #jsx [Counter] el)
