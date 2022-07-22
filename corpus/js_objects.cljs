(ns js-objects)

(let [^js {:keys [a b]} #js {:a 1 :b (+ 1 2 3)}]
  (js/console.log (+ a b)))
