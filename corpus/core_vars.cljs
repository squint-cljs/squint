(ns core-vars)

(def js-map (clj->js (assoc nil :foo :bar)))

(js/console.log js-map)

(def clj-map (assoc nil :foo (+ 1 2 3)))

(js/console.log (get clj-map :foo)) ;; => 6
