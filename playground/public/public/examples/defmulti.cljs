(require '["https://esm.sh/@thi.ng/defmulti" :refer [defmulti]
           ;; renamed: squint has its own defmulti macro, which would win
           :rename {defmulti thi-defmulti}])

(def foo (thi-defmulti (fn [x] x)))

(.isa foo 23 "odd")

(.add foo "odd" (fn [x] (str x " is an odd number")))

(foo 23)
