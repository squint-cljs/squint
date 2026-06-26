(ns squint.dce-test
  "Tree-shaking (DCE) regression. Bundles small slices of squint core with
   esbuild and asserts they stay small. A change that re-anchors the collection
   classes into every bundle (a top-level `fn[SYM] = ...` mutation, or an
   `instanceof` in the central dispatch typeConst/dequal/isVectorArray) balloons
   these slices back to the old ~5.4KB floor and fails here. See doc/dev/dce.md.
   Uses the esbuild JS API (cross-platform; needs 0.18+ for @__NO_SIDE_EFFECTS__,
   devDep is 0.28)."
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   ["esbuild" :as esbuild]))

;; forward slashes so the import path is valid on Windows too
(def ^:private core
  (str/replace (str (js/process.cwd) "/src/squint/core.js") "\\" "/"))

(defn- bundle
  "Minified esbuild bundle of an entry importing `names` from core; returns the
   output JS string. buildSync resolves the per-platform esbuild binary itself."
  [names]
  (let [imp (str/join ", " names)
        res (esbuild/buildSync
             #js {:stdin #js {:contents (str "import { " imp " } from "
                                             (js/JSON.stringify core)
                                             ";\nconsole.log(" imp ");")
                              :loader "js"
                              :resolveDir (js/process.cwd)}
                  :bundle true :minify true :format "esm" :write false})]
    (.-text ^js (aget (.-outputFiles ^js res) 0))))

;; caps sit well below the pre-refactor floor (identity 5377B, atom 5887B) and
;; comfortably above current sizes, so they catch a regression without being
;; brittle to small changes.
(def ^:private cases
  [{:names ["identity"] :cap 800}
   {:names ["atom" "deref" "reset_BANG_" "swap_BANG_"] :cap 2200}
   ;; get/assoc/str/keyword use no lazy seqs: the lazy machinery (marked by
   ;; concat1's "concat-done" symbol) must not be pulled in.
   {:names ["atom" "get" "assoc" "str" "keyword"] :cap 4000 :absent ["concat-done"]}
   ;; conj must dispatch SortedSet by brand, not `instanceof SortedSet`. An
   ;; instanceof pins SortedSet + sort + compare (the "_elts" field is unique to
   ;; SortedSet) into every app that uses conj.
   {:names ["conj"] :cap 3800 :absent ["_elts"]}])

(deftest no-dce-floor-regression
  (doseq [{:keys [names cap absent]} cases]
    (testing (str/join "," names)
      (let [code (bundle names)
            bytes (.-length code)]
        (is (<= bytes cap)
            (str bytes "B exceeds cap " cap "B - DCE floor regression?"))
        (doseq [m (or absent [])]
          (is (not (str/includes? code m))
              (str "contains '" m "' - unused machinery pulled in")))))))
