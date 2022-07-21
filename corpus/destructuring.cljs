(ns destructuring)

(let [{:js/keys [a] :as m} (clj->js {:a 1})
      [b c d] [2 3 4]]
  (prn m a b c d))

(let [{:js/keys [a] :as m} {:js/a 1}]
  (prn m a))
