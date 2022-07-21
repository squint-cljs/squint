(ns destructuring)

;; TODO:
(let [{:keys [a]} {:a 1}
      [b c d] [2 3 4]]
  (js/console.log a b c d)
  )


