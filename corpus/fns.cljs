(ns fns)

(def foo (fn foo [{:keys [a b]}]
           (+ a b)))

(prn (foo {:a 1 :b 2}))

(defn bar [{:keys [a b]}]
  (+ a b))

(prn (bar {:a 1 :b 2}))
