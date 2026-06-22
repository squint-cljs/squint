(ns squint.lazy-memory-test
  "Pins the squint lazy-seq performance contract:
   - caching: elements compute once no matter how often a seq is traversed.
   - streaming: a single forward pass over a large lazy seq that is not
     retained runs in roughly constant memory.

   The seq code is compiled by squint (via jsv!) and run in node, so it
   exercises squint's runtime core.js, not ClojureScript's chunked seqs."
  (:require
   [clojure.test :refer [deftest is testing]]
   [squint.test-utils :refer [eq jsv!]]))

;; --- caching: reuse computes each element once ---

(deftest map-reuse-computes-once
  (is (= 5 (jsv! "(let [calls (atom 0)
                        s (map (fn [x] (swap! calls inc) (* 2 x)) [1 2 3 4 5])]
                    (dorun s) (dorun s) (dorun s)
                    @calls)"))))

(deftest chain-reuse-computes-once
  (is (eq [6 6]
         (jsv! "(let [mc (atom 0) fc (atom 0)
                      s (filter (fn [x] (swap! fc inc) (even? x))
                                (map (fn [x] (swap! mc inc) (inc x)) [1 2 3 4 5 6]))]
                  (dorun s) (dorun s)
                  [@mc @fc])"))))

(deftest take-realizes-only-prefix
  (is (eq [5 8]
         (jsv! "(let [calls (atom 0)
                      s (map (fn [x] (swap! calls inc) x) (range))]
                  (dorun (take 5 s))
                  (let [a @calls]
                    (dorun (take 8 s))
                    [a @calls]))"))
      "take realizes only the prefix; extending reuses the cached prefix"))

;; --- streaming: constant memory on a single non-retained pass ---

(defn- gc-available? []
  (jsv! "(fn? (.-gc js/globalThis))"))

;; Full retention of N cons cells is well over 60MB. A streaming walk stays far
;; below; the ceiling absorbs gc jitter while still failing on full retention.
(def ^:private n 1500000)
(def ^:private ceil-mb 35)

(defn- max-live-mb
  "Compile and run a single forward walk of seq-expr, sampling live heap after
   forced gc. seq-expr is an inline temp in the doseq, never bound to a
   surviving local, so a streaming seq is collectable behind the cursor."
  [seq-expr]
  (jsv! (str "(let [gc (fn [] (dotimes [_ 5] ((.-gc js/globalThis))))
                    heap (fn [] (/ (.-heapUsed (js/process.memoryUsage)) 1048576))]
                (gc)
                (let [base (heap) mx (atom 0) k (atom 0)]
                  (doseq [_ " seq-expr "]
                    (swap! k inc)
                    (when (zero? (mod @k 262144))
                      (gc)
                      (let [h (- (heap) base)] (when (> h @mx) (reset! mx h)))))
                  @mx))")))

(deftest streaming-constant-memory
  (if-not (gc-available?)
    (println "skip streaming assertions (run node with --expose-gc)")
    (doseq [[label seq-expr]
            [["map over lazy" (str "(map inc (range " n "))")]
             ["filter over lazy" (str "(filter even? (range " n "))")]
             ["map+filter over lazy" (str "(map inc (filter even? (range " n ")))")]
             ["take over infinite range" (str "(take " n " (range))")]
             ["concat of two lazy" (str "(concat (range " (quot n 2) ") (range " (quot n 2) "))")]
             ["mapcat over lazy" (str "(mapcat vector (range " n "))")]
             ["rest of lazy" (str "(rest (range " n "))")]
             ["cons onto lazy" (str "(cons 0 (range " n "))")]]]
      (testing label
        (let [mb (max-live-mb seq-expr)]
          (is (< mb ceil-mb)
              (str label " max live " (.toFixed mb 1) "MB exceeds " ceil-mb "MB")))))))
