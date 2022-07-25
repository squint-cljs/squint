(ns js-star)

(js* "console.log(1,2,~{})" 3)

(def x (js* "1 + ~{}" (+ 1 2 3)))

(js/console.log x)
