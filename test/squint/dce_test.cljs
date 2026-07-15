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
  (let [{:keys [javascript]} (squint/compile-string* src {:core-alias "squint_core"})
        js (str/replace javascript "squint-cljs/core.js" core)
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
   ;; includes the IAtom/IDeref/IReset/ISwap/IWatchable slots on Atom
   {:names ["atom" "deref" "reset_BANG_" "swap_BANG_"] :cap 3600}
   ;; get/assoc/str/keyword use no lazy seqs: the lazy machinery (marked by
   ;; concat1's "concat-done" symbol) must not be pulled in.
   {:names ["atom" "get" "assoc" "str" "keyword"] :cap 4000 :absent ["concat-done"]}
   ;; conj must dispatch SortedSet by brand, not `instanceof SortedSet`. An
   ;; instanceof pins SortedSet + sort + compare (the "_elts" field is unique to
   ;; SortedSet) into every app that uses conj.
   {:names ["conj"] :cap 2900 :absent ["_elts"]}
   ;; the full import set of reagami 0.1.38, a whole-lib floor. Unused protocol
   ;; consts must shake out; IWriter's description is the canary.
   {:names ["boolean_QMARK_" "conj" "deref" "disj" "fn_QMARK_" "fnil" "map_QMARK_"
            "min" "not" "nth" "number_QMARK_" "object_QMARK_" "quot" "reduce"
            "run_BANG_" "seq_QMARK_" "string_QMARK_" "subs" "truth_" "update_BANG_"
            "vector_QMARK_" "volatile_BANG_" "vreset_BANG_"]
    :cap 8500 :absent ["squint.core.IWriter"]}])

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

(deftest symbol-defs-stay-pure
  ;; esbuild 0.28 treats Symbol() as side-effect free, but older esbuild (vite
  ;; bundles 0.21-0.25) and other bundlers do not: an unannotated top-level
  ;; Symbol definition pins itself and its protocol const into every consumer
  ;; bundle. The bundler in this suite is too new to catch that via size, so
  ;; check the source directly.
  (let [src (fs/readFileSync core "utf8")
        bad (->> (str/split-lines src)
                 (filter #(str/includes? % "Symbol("))
                 (remove #(str/includes? % "@__PURE__")))]
    (is (empty? bad)
        (str "Symbol( without @__PURE__ annotation:\n" (str/join "\n" bad)))))

(deftest unused-fn-shakes-out
  ;; multi-arity/variadic fns emit a `var f = (() => {...})()` IIFE. Without the
  ;; @__PURE__ annotation the IIFE is a top-level side effect a bundler keeps,
  ;; pinning every unused fn (e.g. compiled macro bodies). The marker is the
  ;; `cljs$lang$maxFixedArity` property the codegen sets - a property name, so it
  ;; survives minification (unlike the `var_args` param, which gets renamed). See
  ;; squint.internal.fn.
  (let [code (bundle-src
              (str "(defn used [a] (inc a))\n"
                   "(defn unused-variadic [& xs] (apply + xs))\n"
                   "(defn unused-multi ([a] a) ([a b] (+ a b)))")
              "used")]
    (is (not (str/includes? code "cljs$lang$maxFixedArity"))
        "unused multi-arity/variadic fn survived bundling - missing @__PURE__?")))
