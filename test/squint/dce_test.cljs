(ns squint.dce-test
  "Tree-shaking (DCE) regression. Bundles small slices of squint core with
   esbuild and asserts they stay small. A change that re-anchors the collection
   classes into every bundle (a top-level `fn[SYM] = ...` mutation, or an
   `instanceof` in the central dispatch typeConst/dequal/isVectorArray) balloons
   these slices back to the old ~5.4KB floor and fails here. See doc/dev/dce.md.
   Uses the esbuild JS API (cross-platform; needs 0.18+ for @__NO_SIDE_EFFECTS__,
   devDep is 0.28)."
  (:require
   ["esbuild" :as esbuild]
   ["fs" :as fs]
   ["os" :as os]
   ["path" :as path]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [squint.compiler :as squint]))

;; forward slashes so the import path is valid on Windows too
(def ^:private core
  (str/replace (str (js/process.cwd) "/src/squint/core.js") "\\" "/"))

(defn- bundle-src
  "Compiles squint `src` to its own module, then bundles a separate entry that
   imports `import-name` from it. Cross-module on purpose: esbuild keeps an
   imported module's top-level statements unless they are @__PURE__ or the
   package opts out via sideEffects, which is exactly what pins unused fns in a
   real build. Returns the minified bundle."
  [src import-name]
  (let [{:keys [pragmas body]} (squint/compile-string* src {:core-alias "squint_core"})
        js (str/replace (str pragmas body) "squint-cljs/core.js" core)
        mod (str/replace (path/join (.realpathSync fs (os/tmpdir))
                                     (str "squint-dce-" (.getTime (js/Date.)) ".mjs"))
                         "\\" "/")]
    (fs/writeFileSync mod js)
    (try
      (let [res (esbuild/buildSync
                 #js {:stdin #js {:contents (str "import { " import-name " } from "
                                                 (js/JSON.stringify mod)
                                                 ";\nconsole.log(" import-name ");")
                                  :loader "js" :resolveDir (js/process.cwd)}
                      :bundle true :minify true :format "esm" :write false})]
        (.-text ^js (aget (.-outputFiles ^js res) 0)))
      (finally (fs/rmSync mod)))))

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

(deftest unused-fn-shakes-out
  ;; multi-arity/variadic fns emit a `var f = (() => {...})()` IIFE. Without the
  ;; @__PURE__ annotation the IIFE is a top-level side effect a bundler keeps,
  ;; pinning every unused fn (e.g. compiled macro bodies). "var_args" is unique
  ;; to the variadic codegen. See squint.internal.fn.
  (let [code (bundle-src
              (str "(defn used [a] (inc a))\n"
                   "(defn unused-variadic [& xs] (apply + xs))\n"
                   "(defn unused-multi ([a] a) ([a b] (+ a b)))")
              "used")]
    (is (not (str/includes? code "var_args"))
        "unused variadic fn survived bundling - missing @__PURE__?")))
