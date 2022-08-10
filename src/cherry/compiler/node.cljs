(ns cherry.compiler.node
  (:require
   ["fs" :as fs]
   [cherry.compiler :as compiler]
   [clojure.string :as str]
   [edamame.core :as e]))

(defn slurp [f]
  (fs/readFileSync f "utf-8"))

(defn spit [f s]
  (fs/writeFileSync f s "utf-8"))

(defn resolve-file [prefix suffix]
  (if (str/starts-with? suffix "./")
    (str/join "/" (concat [(js/process.cwd)]
                          (butlast (str/split prefix "/"))
                          [(subs suffix 2)]))
    suffix))

(def dyn-import (js/eval "(x) => { return import(x) }"))

(defn scan-macros [file]
  (let [s (slurp file)
        maybe-ns (e/parse-next (e/reader s) compiler/cherry-parse-opts)]
    (when (and (seq? maybe-ns)
               (= 'ns (first maybe-ns)))
      (let [[_ns _name & clauses] maybe-ns
            require-macros (some #(when (and (seq? %)
                                             (= :require-macros (first %)))
                                    (rest %))
                                 clauses)]
        (when require-macros
          (reduce (fn [prev require-macros]
                    (.then prev
                           (fn [_]
                             (let [[f & {:keys [refer]}] require-macros
                                   f (resolve-file file f)
                                   macros (-> (dyn-import f)
                                              (.then (fn [macros]
                                                       (zipmap
                                                        refer
                                                        (map #(aget macros (munge %)) refer)))))]
                               (.then macros
                                      (fn [macros]
                                        (set! compiler/built-in-macros
                                              ;; hack
                                              (merge compiler/built-in-macros macros))))))))
                  (js/Promise.resolve nil)
                  require-macros))))))

(defn compile-file [{:keys [in-file out-file]}]
  (-> (js/Promise.resolve (scan-macros in-file))
      (.then #(compiler/compile-string* (slurp in-file)))
      (.then (fn [{:keys [javascript jsx]}]
               (let [out-file (or out-file
                                  (str/replace in-file #".clj(s|c)$"
                                               (if jsx
                                                 ".jsx"
                                                 ".mjs")))]
                 (spit out-file javascript)
                 {:out-file out-file})))))
