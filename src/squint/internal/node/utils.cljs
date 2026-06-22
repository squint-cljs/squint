(ns squint.internal.node.utils
  (:require ["child_process" :as cproc]
            ["fs" :as fs]
            ["path" :as path]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(defn slurp [f]
  (fs/readFileSync f "utf-8"))

(defn resolve-file* [dir munged-macro-ns]
  (let [exts ["cljc" "cljs"]]
    (some (fn [ext]
            (let [full-path (path/resolve dir (str munged-macro-ns "." ext))]
              (when (fs/existsSync full-path)
                full-path)))
          exts)))

(def !cfg (atom nil))

(defn get-cfg []
  (or @!cfg
      (do (reset! !cfg (when (fs/existsSync "squint.edn")
                         (-> (slurp "squint.edn")
                             (edn/read-string))))
          @!cfg)))

(defn set-cfg! [cfg]
  (reset! !cfg cfg))

(defn- dir? [f]
  (and (fs/existsSync f)
       (.isDirectory (fs/lstatSync f))))

(defn deps->paths
  "Resolve a `:deps` map to source directories by shelling out to
  `clojure -Spath`. Returns the classpath directory entries. Jars are
  dropped: only git/local deps are supported for now."
  [deps]
  (let [sdeps (str {:deps deps})
        out (try
              (cproc/execFileSync "clojure"
                                  #js ["-Srepro" "-Sdeps" sdeps "-Spath"]
                                  #js {:encoding "utf-8"
                                       :stdio #js ["ignore" "pipe" "inherit"]})
              (catch :default e
                (if (= "ENOENT" (.-code e))
                  (throw (js/Error. "Resolving :deps requires the `clojure` CLI on PATH."))
                  (throw (js/Error. (str "Failed to resolve :deps via `clojure -Spath`: " (.-message e)))))))
        cp (str/trim out)
        entries (str/split cp (re-pattern path/delimiter))]
    (filterv dir? entries)))

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
  "Read and parse squint.edn from `dir` (defaults to cwd). Returns the config
  map, or nil when there is no squint.edn."
  ([] (read-config "."))
  ([dir]
   (let [f (path/resolve dir "squint.edn")]
     (when (fs/existsSync f)
       (edn/read-string (slurp f))))))

(defn process-opts! [opts]
  (let [file-cfg (get-cfg)
        cfg (expand-paths (merge file-cfg opts))]
    (set-cfg! cfg)
    cfg))

(defn resolve-file
  ([macro-ns]
   (resolve-file macro-ns (:paths (get-cfg) ["." "src"])))
  ([macro-ns paths]
   (let [path (-> macro-ns str (str/replace "-" "_") (str/replace "." "/"))]
     (some (fn [dir]
             (resolve-file* dir path))
           (or paths ["." "src"])))))
