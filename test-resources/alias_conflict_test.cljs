(ns alias-conflict-test (:require ["fs" :as _]))
(prn [(vec (map - [1 2 3]))
      (_/existsSync "README.md")
      (apply - [1 2 (- 10 1)])])
