(ns for)

(prn (new cljs.core/LazySeq nil (fn [] [1 2 3])))

#_(prn
 (for [x [1 2 3]
       y [4 5 6]]
   [x y]))
