(ns squint.compiler.node
  (:require
   ["crypto" :as crypto]
   ["fs" :as fs]
   ["path" :as path]
   [clojure.string :as str]
   [edamame.core :as e]
   [shadow.esm :as esm]
   [squint.compiler :as compiler]
   [squint.internal.node.utils :as utils]))

(def sci (atom nil))

;; Tracks macro source files so we only re-eval (`:reload`) a macro namespace
;; when its file actually changed. Without this, the persistent SCI instance
;; keeps the first-loaded macro defs forever in watch mode (issue #819);
;; reloading on every compile would re-eval untouched macro nses (measured
;; ~1-5ms each) instead of a microsecond stat. path -> {:mtime _ :sha _}.
(def macro-state (atom {}))

(defn slurp [f]
  (fs/readFileSync f "utf-8"))

(defn spit [f s]
  (fs/writeFileSync f s "utf-8"))

(defn- sha256 [s]
  (-> (crypto/createHash "sha256") (.update s) (.digest "hex")))

(defn- macro-file-changed?
  "True when macro-ns's source file changed since last seen. Cheap mtime gate
  first; only on mtime change do we read + sha256 to confirm real content
  change (suppresses spurious reloads on touch-without-edit). Updates cache."
  [macro-ns]
  (when-let [path (utils/resolve-file macro-ns)]
    (let [{:keys [mtime sha]} (get @macro-state path)
          cur-mtime (.-mtimeMs (fs/statSync path))]
      (when (not= cur-mtime mtime)
        (let [cur-sha (sha256 (slurp path))]
          (swap! macro-state assoc path {:mtime cur-mtime :sha cur-sha})
          (not= cur-sha sha))))))

(defn- cljc-with-macros?
  "Check if a require clause refers to a .cljc file that contains defmacro."
  [libspec]
  (let [libname (if (symbol? libspec) libspec (first libspec))]
    (when (symbol? libname)
      (when-let [path (utils/resolve-file libname)]
        (and (str/ends-with? path ".cljc")
             (str/includes? (slurp path) "defmacro"))))))

(defn scan-macros [s {:keys [ns-state]}]
  (let [maybe-ns (e/parse-next (e/reader s) compiler/squint-parse-opts)]
    (when (and (seq? maybe-ns)
               (= 'ns (first maybe-ns)))
      (let [[_ns the-ns-name & clauses] maybe-ns
            [require-macros reload] (some (fn [[clause reload]]
                                            (when (and (seq? clause)
                                                       (= :require-macros (first clause)))
                                              [(rest clause) reload]))
                                          (partition-all 2 1 clauses))
            ;; also scan :require clauses for .cljc files that may contain macros
            require-cljc (some->> clauses
                                  (some (fn [clause]
                                          (when (and (seq? clause)
                                                     (= :require (first clause)))
                                            (rest clause))))
                                  (filter cljc-with-macros?))
            all-macro-requires (concat require-macros require-cljc)]
        (when (seq all-macro-requires)
          (.then (esm/dynamic-import "./compiler.sci.js")
                 (fn [_]
                   (let [eval-form (:eval-form @sci)]
                     (.then
                      (reduce
                       (fn [prev require-macros]
                         (.then prev
                                (fn [_]
                                  (let [;; a libspec may be a bare symbol, normalize to vector
                                        require-macros (if (symbol? require-macros)
                                                         [require-macros]
                                                         require-macros)
                                        [macro-ns & {:keys [refer refer-macros as]}] require-macros
                                        refer (or refer refer-macros)
                                        reload? (or reload (macro-file-changed? macro-ns))
                                        macros (js/Promise.resolve
                                                (do (eval-form (cond-> (list 'require (list 'quote macro-ns))
                                                                 reload? (concat [:reload])))
                                                    (let [publics (eval-form
                                                                   `(ns-publics '~macro-ns))
                                                          macros (keep (fn [[k v]]
                                                                         (when (:macro (meta v))
                                                                           [k (deref v)])) publics)
                                                          macros (into {} macros)]
                                                      macros)))]
                                    (.then macros
                                           (fn [macros]
                                             (swap! ns-state (fn [ns-state]
                                                               (cond-> (assoc-in ns-state [:macros macro-ns] macros)
                                                                 as (assoc-in [the-ns-name :aliases as] macro-ns)
                                                                 refer (update-in [the-ns-name :refers] merge (zipmap refer (repeat macro-ns))))))))))))
                       (js/Promise.resolve nil)
                       all-macro-requires)
                      (fn [_]
                        ;; register lazy macro resolver for transitive deps
                        (swap! ns-state assoc :resolve-macro
                               (fn [ns-sym name-sym]
                                 (let [fqn (symbol (str ns-sym) (str name-sym))]
                                   (eval-form
                                    (list 'when-let ['v (list 'resolve (list 'quote fqn))]
                                          (list 'when (list ':macro (list 'meta 'v))
                                                (list 'deref 'v)))))))))))))))))

(defn default-ns-state []
  (atom {:current 'user}))

(defn ->opts [opts]
  (assoc opts :ns-state (or (:ns-state opts) (default-ns-state))))

(defn compile-string [contents opts]
  (let [opts (->opts opts)]
    (-> (js/Promise.resolve (scan-macros contents opts))
        (.then #(compiler/compile-string* contents opts)))))

(defn in-dir? [dir file]
  (let [dir (.split ^js (path/resolve dir) path/sep)
        file (.split ^js (path/resolve file) path/sep)]
    (loop [dir dir
           file file]
      (or (empty? dir)
          (and (seq file)
               (= (first dir)
                  (first file))
               (recur (rest dir)
                      (rest file)))))))

(defn adjust-file-for-paths [in-file paths]
  (let [out-file (reduce (fn [acc path]
                           (if (in-dir? path in-file)
                             (reduced (path/relative path in-file))
                             acc))
                         in-file
                         paths)]
    out-file))

(defn file-in-output-dir [file paths output-dir]
  (if output-dir
    (path/resolve output-dir
                  (adjust-file-for-paths file paths))
    file))

(defn- with-output-extension
  ;; Swap to `extension` (default .mjs) and use "/" for ESM specifiers.
  [file extension]
  (let [ext (or extension ".mjs")
        ext (if (str/starts-with? ext ".") ext (str "." ext))]
    (-> file
        (str/replace (re-pattern (str (path/extname file) "$")) ext)
        (str/replace "\\" "/"))))

(defn- compiled-output-path
  ;; Absolute path to ns `x`'s compiled output module, or nil for a non-local ns.
  [x paths output-dir extension]
  (some-> (utils/resolve-file x paths)
          (file-in-output-dir paths output-dir)
          (with-output-extension extension)))

(defn resolve-ns
  "Resolve required ns `x` to its compiled output path, relative to `in-file`'s
  output location. Nil when `x` is not a local source ns."
  [{:keys [output-dir paths extension]} in-file x]
  (when-let [abs (compiled-output-path x paths output-dir extension)]
    (let [base (path/dirname (str (file-in-output-dir in-file paths output-dir)))]
      (str "./" (str/replace (path/relative base abs) "\\" "/")))))

(defn resolve-ns-repl
  "Like `resolve-ns` but returns an absolute path. The REPL evals in squint's lib
  dir where a relative specifier cannot resolve."
  [x]
  (let [{:keys [output-dir paths extension]} (utils/expand-paths (or (utils/get-cfg) {}))]
    (compiled-output-path x (or paths ["." "src"]) (or output-dir ".") extension)))

(defn compile-file [{:keys [in-file in-str out-file extension output-dir]
                     :or {output-dir ""}
                     :as opts}]
  (let [contents (or in-str (slurp in-file))
        opts (->opts opts)
        ;; When the caller didn't supply :resolve-ns, wire squint's own
        ;; squint.edn-style resolution so local cross-ns requires compile to a
        ;; relative path (./foo.js) instead of a bare 'foo' a bundler can't
        ;; resolve. Driven by the caller-passed :paths/:output-dir/:extension.
        opts (cond-> opts
               (and in-file (not (:resolve-ns opts)))
               (assoc :resolve-ns (fn [x] (resolve-ns opts in-file x))))]
    (-> (compile-string contents (assoc opts :ns nil))
        (.then (fn [{:keys [javascript jsx] :as opts}]
                 (let [opts (utils/process-opts! opts)
                       paths (:paths opts ["." "src"])
                       ;; Raw JSX tags need a .jsx extension so a downstream
                       ;; transform (e.g. @vitejs/plugin-react) picks them up.
                       ;; With :jsx-runtime the output is plain JS (jsx() calls),
                       ;; so honor the configured extension instead.
                       jsx-tags? (and jsx (not (:jsx-runtime opts)))
                       out-file (path/resolve output-dir
                                              (or out-file
                                                  (str/replace (adjust-file-for-paths in-file paths) #".clj(s|c)$"
                                                               (if jsx-tags?
                                                                 ".jsx"
                                                                 (or (when-let [ext extension]
                                                                       (str "." (str/replace ext #"^\." "")))
                                                                     ".mjs")))))
                       out-path (path/dirname out-file)]
                   (when-not (fs/existsSync out-path)
                     (fs/mkdirSync out-path #js {:recursive true}))
                   (when-not (fs/existsSync out-path)
                     (throw (js/Error. "File not found, make sure output-dir is a valid path: "
                                       {:output-dir output-dir
                                        :out-file out-file})))
                   (spit out-file javascript)
                   (assoc opts :out-file out-file)))))))

(defn ->clj [x]
  (js->clj x :keywordize-keys true))

(defn- jsify [f]
  (fn [& args]
    (let [args (mapv ->clj args)
          ret (apply f args)]
      (if (instance? js/Promise ret)
        (.then ret clj->js)
        (clj->js ret)))))

#_{:clj-kondo/ignore [:unused-private-var]}
(def ^:private compile-string-js
  (jsify compile-string))

#_{:clj-kondo/ignore [:unused-private-var]}
(def ^:private compile-file-js
  (jsify compile-file))

#_{:clj-kondo/ignore [:unused-private-var]}
(def ^:private read-config-js
  (jsify utils/read-config))

#_{:clj-kondo/ignore [:unused-private-var]}
(def ^:private deps-paths-js
  (jsify utils/deps-paths))
