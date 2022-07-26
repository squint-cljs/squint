(ns fns)

(def results (atom []))

(defn add! [x]
  (swap! results conj x))

(def foo (fn foo [{:keys [a b]}]
           (+ a b)))

(add! (foo {:a 1 :b 2}))

(defn bar [{:keys [a b]}]
  (+ a b))

(add! (bar {:a 1 :b 2}))

(defn baz [^js {:keys [a b]}]
  (+ a b))

(add! (baz #js {:a 1 :b 2}))

(defn quux [x]
  (if (pos? x)
    (recur (dec x))
    x))

(add! (quux 5))

(defn mfoo
  ([x] x)
  ([_x y] y))

(add! (mfoo 1))
(add! (mfoo 1 2))
