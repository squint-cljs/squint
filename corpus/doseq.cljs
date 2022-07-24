(ns doseq)

(doseq [x [1 2 3]
        y [:hello :bye]]
  (prn x y))
