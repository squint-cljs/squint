(defn ^:async js-require [url module]
  (let [module (or module {:exports {}})
        resp (await (js/fetch url))
        script (await (.text resp))
        func (js/Function "module" "exports" script)]
    (.call func module module (.-exports module))
    (.-exports module)))

(def equal (await (js-require "https://unpkg.com/fast-deep-equal@3.1.3/index.js")))

(equal [1 2 3] [1 2 3])
