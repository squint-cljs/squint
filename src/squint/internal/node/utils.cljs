(ns squint.internal.node.utils
  (:require ["fs" :as fs]
            ["path" :as path]
            [clojure.string :as str]))

(defn resolve-file* [dir munged-macro-ns]
  (let [exts ["cljc" "cljs"]]
    (some (fn [ext]
            (let [full-path (path/resolve dir (str munged-macro-ns "." ext))]
              (when (fs/existsSync full-path)
                full-path)))
          exts)))

(defn resolve-file [macro-ns {:keys [paths]}]
  (let [path (-> macro-ns str (str/replace "-" "_") (str/replace "." "/"))]
    (prn :path path :paths paths)
    (some (fn [dir]
            (resolve-file* dir path))
          paths)))
