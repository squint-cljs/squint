(ns squint.internal.node.utils
  (:require ["fs" :as fs]
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

(defn process-opts! [opts]
  (let [file-cfg (get-cfg)
        cfg (merge file-cfg opts)]
    (set-cfg! cfg)
    cfg))

(defn resolve-file
  [macro-ns]
  (let [path (-> macro-ns str (str/replace "-" "_") (str/replace "." "/"))]
    (some (fn [dir]
            (resolve-file* dir path))
          (:paths (get-cfg) ["." "src"]))))
