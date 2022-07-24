(ns fns)

(def foo (fn foo [{:keys [a b]}]
           (+ a b)))

(prn (foo {:a 1 :b 2}))

(defn bar [{:keys [a b]}]
  (+ a b))

(prn (bar {:a 1 :b 2}))

(defn baz [^js {:keys [a b]}]
  (+ a b))

(prn (baz #js {:a 1 :b 2}))

(defn quux [x]
  (if (pos? x)
    (recur (dec x))
    x))

(prn (quux 5))
