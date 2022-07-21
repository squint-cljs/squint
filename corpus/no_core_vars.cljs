(ns no-core-vars)

;; The expectation is that when bundling this, it will only be a couple of bytes.

(defn foo []
  [])

(js/console.log (str (foo)))
