(ns squint.compiler-test
  (:require [squint.compiler :as sq]
            [clojure.string :as str]))

(defn to-js [code {:keys [requires]}]
  (sq/compile-string
   (str "(ns module"
        "(:require " (str/join "\n" requires) "))"
        code)))


(comment

  (to-js
   '(let [a 123]

      (println a)
      )
   [])
  )
