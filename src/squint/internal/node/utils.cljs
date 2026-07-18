(ns squint.internal.node.utils
  (:require ["child_process" :as cproc]
            ["fs" :as fs]
            ["path" :as path]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [squint.compiler-common :as cc]))

(defn slurp [f]
  (fs/readFileSync f "utf-8"))

(defn resolve-file* [dir munged-macro-ns]
  (let [exts ["cljc" "cljs"]]
    (some (fn [ext]
            (let [full-path (path/resolve dir (str munged-macro-ns "." ext))]
              (when (fs/existsSync full-path)
                full-path)))
          exts)))

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

(def default-config-file "squint.edn")

;; config file name -> effective config. Keyed so cherry and squint can
;; coexist in one build, each reading its own config file.
(def !cfg (atom {}))

(defn get-cfg
  ([] (get-cfg default-config-file))
  ([config-file]
   (or (get @!cfg config-file)
       (let [cfg (when (fs/existsSync config-file)
                   (-> (slurp config-file)
                       (edn/read-string)))]
         (swap! !cfg assoc config-file cfg)
         cfg))))

(defn set-cfg!
  ([cfg] (set-cfg! default-config-file cfg))
  ([config-file cfg]
   (swap! !cfg assoc config-file cfg)
   cfg))

(defn- dep-dir?
  "A classpath entry that is an external source directory: absolute (the
  project's own relative :paths like \"src\"/\"resources\" that clojure injects
  are dropped) and an existing directory (jars are dropped)."
  [f]
  (and (path/isAbsolute f)
       (fs/existsSync f)
       (.isDirectory (fs/lstatSync f))))

(defn deps->paths
  "Resolve a `:deps` map to source directories by shelling out to
  `clojure -Spath`. Returns the classpath directory entries. Jars are
  dropped: only git/local deps are supported for now. `dir` is the working
  directory clojure runs in, so `:local/root` relative paths resolve against it."
  ([deps] (deps->paths deps nil))
  ([deps dir]
   (let [sdeps (str {:deps deps})
         out (try
               (cproc/execFileSync "clojure"
                                   #js ["-Srepro" "-Sdeps" sdeps "-Spath"]
                                   #js {:encoding "utf-8"
                                        :cwd (or dir (js/process.cwd))
                                        :stdio #js ["ignore" "pipe" "inherit"]})
               (catch :default e
                 (if (= "ENOENT" (.-code e))
                   (throw (js/Error. "Resolving :deps requires the `clojure` CLI on PATH."))
                   (throw (js/Error. (str "Failed to resolve :deps via `clojure -Spath`: " (.-message e)))))))
         cp (str/trim out)
         entries (str/split cp (re-pattern path/delimiter))]
     (filterv dep-dir? entries))))

(def ^:private !deps-paths (atom {}))

(defn- resolve-deps-paths [deps]
  (or (get @!deps-paths deps)
      (let [ps (deps->paths deps)]
        (swap! !deps-paths assoc deps ps)
        ps)))

(defn expand-paths
  "When `opts` declares `:deps`, resolve them to directories and append those
  to `:paths`. Result is memoized per deps map. No-op without `:deps`."
  [opts]
  (if-let [deps (:deps opts)]
    (let [dep-paths (resolve-deps-paths deps)
          paths (vec (distinct (concat (:paths opts ["." "src"]) dep-paths)))]
      (assoc opts :paths paths))
    opts))

(defn read-config
  "Read and parse the config file (`config-file` names it, default squint.edn)
  from `dir` (defaults to cwd). Returns the config map, or nil when there is
  no config file."
  ([] (read-config "."))
  ([dir] (read-config dir default-config-file))
  ([dir config-file]
   (let [f (path/resolve dir config-file)]
     (when (fs/existsSync f)
       (edn/read-string (slurp f))))))

(defn deps-paths
  "Resolve the `:deps` of the config file in `dir` to absolute source
  directories. Returns [] when there is no config file or no `:deps`. Kept
  separate from JS callers reading the raw config: the deps map has symbol keys
  that would not survive a clj<->js round-trip."
  ([dir] (deps-paths dir default-config-file))
  ([dir config-file]
   (let [cfg (read-config dir config-file)]
     (if-let [deps (:deps cfg)]
       (deps->paths deps dir)
       []))))

(defn process-opts!
  ([opts] (process-opts! default-config-file opts))
  ([config-file opts]
   (let [file-cfg (get-cfg config-file)
         cfg (expand-paths (merge file-cfg opts))]
     (set-cfg! config-file cfg)
     cfg)))

(defn resolve-file
  ([macro-ns]
   (resolve-file macro-ns (:paths (get-cfg) ["." "src"])))
  ([macro-ns paths]
   (let [path (-> macro-ns str (str/replace "-" "_") (str/replace "." "/"))]
     (some (fn [dir]
             (resolve-file* dir path))
           (or paths ["." "src"])))))

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

(defn compiled-output-path
  "Absolute path to ns `x`'s compiled output module, or nil for a non-local ns."
  [x paths output-dir extension]
  (some-> (resolve-file x paths)
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
  "Like `resolve-ns` but returns an absolute path. The REPL evals in the
  dialect's lib dir where a relative specifier cannot resolve."
  ([x] (resolve-ns-repl x default-config-file))
  ([x config-file]
   (let [{:keys [output-dir paths extension]} (expand-paths (or (get-cfg config-file) {}))]
     (compiled-output-path x (or paths ["." "src"]) (or output-dir ".") extension))))

(defn dev-hooks
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
