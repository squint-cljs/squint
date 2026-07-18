(ns squint.compiler.node
  (:require
   ["crypto" :as crypto]
   ["fs" :as fs]
   ["path" :as path]
   [clojure.string :as str]
   [edamame.core :as e]
   [shadow.esm :as esm]
   [squint.compiler :as compiler]
   [squint.compiler-common :as cc]
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

;;;; Squint compile-time extraction (opt-in, additive to the loading above)
;;
;; A namespace flagged `(ns foo {:squint/compile-time true} ...)` has its
;; compile-time part loaded into SCI as ns `foo`: every top-level defmacro
;; squint's normal reader can see, plus forms marked `^:squint/compile-time` -
;; including a marked form inside a #?(:clj ...) branch, the author's opt-in for
;; code the target reader never sees. Unmarked :clj branches are elided, so
;; JVM-only code (e.g. a macro reading JVM config at expansion time) never
;; reaches SCI. The rest of the (possibly SCI-hostile) runtime namespace is
;; never evaluated. Works the same for a .cljc or a .cljs runtime file.
;; Non-flagged namespaces are untouched and keep the exact require-based
;; behavior above.
;;
;; An emitted ref (put into the expansion) resolves at the consumer's runtime;
;; the ns's require aliases are carried as :as-alias so an aliased emitted ref
;; like `str/join` qualifies without loading the ns in SCI. A ref called at
;; expansion time must be loadable in SCI: a built-in (clojure.string) or a
;; same-ns marked helper works.

(defn- lib-name [libspec]
  (if (symbol? libspec) libspec (first libspec)))

(defn- ns-flag
  "The {:squint/compile-time ...} flag value of an ns form, read from the ns
  name's metadata or the attr-map."
  [ns-form]
  (or (:squint/compile-time (meta (second ns-form)))
      (some (fn [x] (and (map? x) (:squint/compile-time x))) (nnext ns-form))))

(defn- lib-flag
  "The compile-time flag value of a required lib's ns form, or nil."
  [libspec]
  (let [libname (lib-name libspec)]
    (when (symbol? libname)
      (when-let [path (utils/resolve-file libname)]
        (let [ns-form (e/parse-next (e/reader (slurp path)) compiler/squint-parse-opts)]
          (when (and (seq? ns-form) (= 'ns (first ns-form)))
            (ns-flag ns-form)))))))

(defn- ns-clause [ns-form kw]
  (some (fn [c] (when (and (seq? c) (= kw (first c))) c)) (nnext ns-form)))

(defn- as-alias-clause
  "The ns's symbol `:require` aliases as `(:require [lib :as-alias a] ...)`, so an
  aliased emitted ref (`str/join`) resolves when SCI reads the extracted source,
  without loading the (possibly runtime-only) namespace."
  [ns-form]
  (let [aliases (some->> (ns-clause ns-form :require) rest
                         (keep (fn [spec]
                                 (when (vector? spec)
                                   (let [[lib & {:keys [as]}] spec]
                                     (when (and as (symbol? lib)) [lib :as-alias as])))))
                         seq)]
    (when aliases (cons :require aliases))))

(def ^:private extract-parse-opts
  ;; squint's normal branch resolution (:squint/:cljs/:default, form order) plus
  ;; one extension: a :clj branch explicitly marked ^:squint/compile-time wins.
  ;; Unmarked :clj branches are elided, so JVM-only code never reaches SCI. The
  ;; chosen branch's own source span is stashed (the parser re-attaches the outer
  ;; conditional's location to the result), so extraction slices the inner form,
  ;; without the #?(...) wrapper - the extracted source contains no :clj
  ;; conditionals and loads in the normal SCI ctx.
  (assoc compiler/squint-parse-opts
         :end-location true
         :auto-resolve-ns true
         :read-cond
         (fn [branches]
           (loop [ps (partition 2 branches)]
             (if (seq ps)
               (let [[k v] (first ps)]
                 (if (or (contains? #{:squint :cljs :default} k)
                         (and (= :clj k) (:squint/compile-time (meta v))))
                   (if (e/iobj? v)
                     (vary-meta v (fn [m]
                                    (assoc m :squint/inner-span
                                           (select-keys m [:line :column :end-row :end-col]))))
                     v)
                   (recur (next ps))))
               e/continue)))))

(defn- slice
  "The substring of `lines` from [sl sc] to [el ec], 0-based, ec exclusive."
  [lines sl sc el ec]
  (if (= sl el)
    (subs (nth lines sl) sc ec)
    (str/join "\n"
              (concat [(subs (nth lines sl) sc)]
                      (map #(nth lines %) (range (inc sl) el))
                      [(subs (nth lines el) 0 ec)]))))

(defn- form-text
  "A top-level form's verbatim source: its stashed inner span when it came out
  of a reader conditional, else its own location."
  [lines form]
  (let [m (meta form)
        {:keys [line column end-row end-col]} (or (:squint/inner-span m) m)]
    (slice lines (dec line) (dec column) (dec end-row) (dec end-col))))

(defn- extraction-source
  "Override source for a flagged .cljc/.cljs: `(ns foo <refer-clojure> <aliases>)`
  plus the compile-time forms - top-level defmacros and ^:squint/compile-time
  marked forms - as their verbatim source text, so SCI's own reader resolves
  syntax-quote (special forms stay, core qualifies to clojure.core, bare same-ns
  refs qualify to this ns). Runtime forms are dropped."
  [src]
  (let [lines (str/split-lines src)
        forms (e/parse-string-all src extract-parse-opts)
        ns-form (some (fn [f] (when (and (seq? f) (= 'ns (first f))) f)) forms)
        compile-time-text (keep (fn [f]
                                  (when (and (seq? f)
                                             (or (:squint/compile-time (meta f))
                                                 (= 'defmacro (first f))))
                                    (form-text lines f)))
                                forms)]
    (str/join "\n"
              (list* (pr-str (concat (list 'ns (second ns-form))
                                     (when-let [rc (ns-clause ns-form :refer-clojure)] (list rc))
                                     (when-let [al (as-alias-clause ns-form)] (list al))))
                     compile-time-text))))

(defn- source-flag
  "The compile-time flag value of a source string's ns form, or nil."
  [src]
  (let [ns-form (e/parse-next (e/reader src) compiler/squint-parse-opts)]
    (when (and (seq? ns-form) (= 'ns (first ns-form)))
      (ns-flag ns-form))))

(defn compile-time-source
  "The compile-time source a flagged ns loads into SCI: the extracted
  compile-time forms. nil when not flagged."
  [src]
  (when (source-flag src) (extraction-source src)))

(defn as-alias? [libspec]
  (and (sequential? libspec)
       (some #{:as-alias} libspec)))

(defn self-ref? [the-ns-name libspec]
  (and (sequential? libspec) (= the-ns-name (first libspec))))

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
            require-libs (some->> clauses
                                  (some (fn [clause]
                                          (when (and (seq? clause)
                                                     (= :require (first clause)))
                                            (rest clause)))))
            require-libs (remove #(self-ref? the-ns-name %) require-libs)
            ;; flagged {:squint/compile-time ...} libs -> the SCI load-fn serves
            ;; their compile-time source; other .cljc-with-defmacro libs ->
            ;; whole-ns (legacy)
            flagged (filter lib-flag require-libs)
            require-cljc (->> require-libs
                              (remove as-alias?)
                              (remove lib-flag) (filter cljc-with-macros?))
            all-macro-requires (concat require-macros require-cljc flagged)]
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

(def in-dir? utils/in-dir?)

(def adjust-file-for-paths utils/adjust-file-for-paths)

(def file-in-output-dir utils/file-in-output-dir)

(def compiled-output-path utils/compiled-output-path)

(def resolve-ns utils/resolve-ns)

(def resolve-ns-repl utils/resolve-ns-repl)

(defn- dev-hooks
  "Munged globalThis paths (\"tic_tac_toe.core.re_render\") of the current ns's
  vars tagged ^:dev/before-load / ^:dev/after-load, for hot-reload tooling."
  [ns-state]
  (let [state @ns-state
        current (:current state)
        hooks (fn [k]
                (into [] (keep (fn [[sym m]]
                                 (when (and (symbol? sym) (map? m) (get m k))
                                   (str (munge current) "." (cc/munge* sym)))))
                      (get state current)))]
    {:before-load (hooks :dev/before-load)
     :after-load (hooks :dev/after-load)}))

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
                   (cond-> (assoc opts :out-file out-file)
                     (:repl opts) (assoc :dev-hooks (dev-hooks (:ns-state opts))))))))))

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
