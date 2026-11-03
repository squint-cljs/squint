(require '["https://unpkg.com/@bloomberg/record-tuple-polyfill" :as tc39])

(def a (js/Record {:a 1}))
(def b (js/Record {:a 1}))
(= a b) ;; true, thank god
(def store (new js/Map))
(.set store a "keyed by collection a")
;; although we get the value from the map using b, we get the same value out as a
(.get store b) ;;=> "keyed by collection a"
(def t1 (js/Tuple 1 2))
(def t2 (js/Tuple 1 2))
(def my-map (new js/Map [[t1 :hello] [t2 :there]]))
(get my-map t1) ;;=> there
(contains? #{t1} t2) ;; true
(count #{t1 t2}) ;; 1 ;; thank god
