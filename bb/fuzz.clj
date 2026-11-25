(ns fuzz
  "Runs squint core fns on edge inputs and diffs the results against nbb."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]))

(def idx ["-1" "0" "1" "2" "3" "4" "1.5" "-0.5" "##NaN" "##Inf" "##-Inf"
          "9007199254740993" "1e300" "nil"])

(def colls ["[]" "[1 2 3]" "'(1 2 3)" "\"abc\"" "(range 5)" "#{1 2}"
            "{\"a\" 1 \"b\" 2}" "nil" "(seq [1 2 3])" "(map inc [0 1 2])"
            "(lazy-seq [1 2 3])"])

(def vecs ["[]" "[1 2 3]" "[0 1 2 3 4]"])

(def strs ["\"\"" "\"abc\"" "\"hello\""])

(def nums ["7" "-7" "2.5" "-2.5" "0" "-0.0" "3" "-3" "##NaN" "##Inf" "##-Inf"
           "1e300" "9007199254740993"])

(def hash-pairs
  "Pairs of = values that squint hashes by value."
  [["1" "1.0"] ["\"a\"" "\"a\""] ["0" "-0.0"] ["nil" "nil"] ["true" "true"]])

(defn- combos [tmpl & domains]
  (map #(apply format tmpl %)
       (reduce (fn [acc d] (for [a acc x d] (conj a x))) [[]] domains)))

(defn cases []
  (concat
   (combos "(nth %s %s)" colls idx)
   (combos "(nth %s %s \"nf\")" colls idx)
   (combos "(get %s %s)" colls idx)
   (combos "(get %s %s \"nf\")" colls idx)
   (combos "(subvec %s %s)" vecs idx)
   (combos "(subvec %s %s %s)" vecs idx idx)
   (combos "(subs %s %s)" strs idx)
   (combos "(subs %s %s %s)" strs idx idx)
   (combos "(assoc %s %s \"x\")" vecs idx)
   (combos "(peek %s)" colls)
   (combos "(pop %s)" colls)
   (combos "(take %s %s)" idx colls)
   (combos "(drop %s %s)" idx colls)
   (combos "(take-last %s %s)" idx colls)
   (combos "(drop-last %s %s)" idx colls)
   (combos "(nthrest %s %s)" colls idx)
   (combos "(nthnext %s %s)" colls idx)
   (combos "(split-at %s %s)" idx colls)
   (combos "(partition %s %s)" idx colls)
   (combos "(partition-all %s %s)" idx colls)
   (combos "(take-nth %s %s)" idx colls)
   (combos "(repeat %s \"x\")" idx)
   (combos "(range %s)" idx)
   (combos "(range %s %s)" idx idx)
   (combos "(mod %s %s)" nums nums)
   (combos "(rem %s %s)" nums nums)
   (combos "(quot %s %s)" nums nums)
   (combos "(compare %s %s)" nums nums)
   (for [[a b] hash-pairs]
     (format "(let [a %s b %s] [(= a b) (= (hash a) (hash b))])" a b))))

(def prelude
  "(ns fuzz-cases)
(defn norm [x]
  (cond (string? x) x
        (map? x) x
        (set? x) x
        (and (some? x) (seqable? x)) (mapv norm (take 50 x))
        :else x))
(def start (js/parseInt (or (.-FUZZ_START (.-env js/process)) \"0\")))
(defn run [label f]
  (when (>= label start)
    (println label (try (pr-str (norm (f))) (catch :default _e \"ERR\")))))
")

(defn- parse-out [s]
  (into {} (keep (fn [line]
                   (when-let [[_ n v] (re-matches #"(\d+) (.*)" line)]
                     [(parse-long n) v])))
        (str/split-lines s)))

(defn- run-all
  "Runs cmd over all n cases.
  Records a case that hangs as HANG and resumes after it."
  [dir n cmd]
  (let [out (fs/file dir "out.txt")]
    (loop [start 0 acc {}]
      (let [proc (p/process (merge {:dir (str dir) :out out :err :string
                             :extra-env {"FUZZ_START" (str start)}}
                            {:cmd cmd}))
            res (deref proc 3000 ::timeout)
            acc (merge acc (parse-out (slurp out)))]
        (if (= ::timeout res)
          (let [_ (p/destroy-tree proc)
                hung (inc (reduce max (dec start) (filter #(>= % start) (keys acc))))]
            (if (< (inc hung) n)
              (recur (inc hung) (assoc acc hung "HANG"))
              (assoc acc hung "HANG")))
          (do (when (seq (:err res)) (println (first cmd) "stderr:" (:err res)))
              acc))))))

(defn -main [& _]
  (let [dir (fs/create-dirs "target/fuzz")
        cs (vec (cases))
        src (str prelude
                 (str/join "\n" (map-indexed (fn [i c] (format "(run %d (fn [] %s))" i c)) cs)))
        _ (spit (str (fs/path dir "fuzz_cases.cljs")) src)
        nbb-out (run-all (fs/create-dirs (fs/path dir "nbb")) (count cs)
                         ["nbb" "../fuzz_cases.cljs"])
        sq-out (run-all (fs/create-dirs (fs/path dir "squint")) (count cs)
                        ["node" (str (fs/absolutize "node_cli.js")) "run" "../fuzz_cases.cljs"])
        diffs (for [i (range (count cs))
                    :let [a (get nbb-out i "MISSING") b (get sq-out i "MISSING")]
                    :when (not= a b)]
                [(cs i) a b])]
    (doseq [[form a b] diffs]
      (println (format "%-55s cljs: %-25s squint: %s" form a b)))
    (println (format "%d cases, %d mismatches" (count cs) (count diffs)))))
