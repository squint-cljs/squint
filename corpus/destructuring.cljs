(ns destructuring)

(let [^js {:keys [a] :as m} (clj->js {:a 1})
      ^js {:js/keys [b]} (js-obj "js/b" :js/b)
      [c d e] [2 3 4]]
  (prn m a b c d e))
