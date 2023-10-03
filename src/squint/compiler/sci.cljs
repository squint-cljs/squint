(ns squint.compiler.sci
  (:require ["fs" :as fs]
            ["path" :as path]
            [clojure.string :as str]
            [squint.compiler.node :refer [sci]]
            [sci.core :as sci]))

(defn slurp [f]
  (fs/readFileSync f "utf-8"))

(def !cfg (atom nil))

(defn resolve-file* [dir munged-macro-ns]
  (let [exts ["cljc" "cljs"]]
    (some (fn [ext]
            (let [full-path (path/resolve dir (str munged-macro-ns "." ext))]
              (when (fs/existsSync full-path)
                full-path)))
          exts)))

(def classpath-dirs ["." "src"])

(defn resolve-file [macro-ns {:keys [paths]
                              :or {paths classpath-dirs}}]
  (prn :paths paths :cfg @!cfg)
  (let [path (-> macro-ns str (str/replace "-" "_") (str/replace "." "/"))]
    (some (fn [dir]
            (resolve-file* dir path))
          paths)))

(def ctx (sci/init {:load-fn (fn [{:keys [namespace]}]
                               (let [f (resolve-file namespace @!cfg)
                                     fstr (slurp f)]
                                 {:source fstr}))
                    :classes {:allow :all
                              'js js/globalThis}}))

(sci/alter-var-root sci/print-fn (constantly *print-fn*))
(sci/alter-var-root sci/print-err-fn (constantly *print-err-fn*))

(sci/enable-unrestricted-access!)

(defn init []
  (reset! sci {:resolve-file resolve-file
               :eval-form (fn [form cfg]
                            (when cfg (reset! !cfg cfg))
                            (sci/eval-form ctx form))}))
