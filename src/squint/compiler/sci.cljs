(ns squint.compiler.sci
  (:require ["fs" :as fs]
            ["path" :as path]
            [clojure.string :as str]
            [squint.compiler.node :refer [sci]]
            [sci.core :as sci]))

(defn slurp [f]
  (fs/readFileSync f "utf-8"))

(defn resolve-file* [dir munged-macro-ns]
  (let [exts ["cljc" "cljs"]]
    (some (fn [ext]
            (let [full-path (path/resolve dir (str munged-macro-ns "." ext))]
              (when (fs/existsSync full-path)
                full-path)))
          exts)))

(def classpath-dirs ["." "src"])

(defn resolve-file [macro-ns]
  (let [path (-> macro-ns str (str/replace "-" "_"))]
    (some (fn [dir]
            (resolve-file* dir path))
          classpath-dirs)))

(def ctx (sci/init {:load-fn (fn [{:keys [namespace]}]
                               (let [f (resolve-file namespace)
                                     fstr (slurp f)]
                                 {:source fstr}))
                    :classes {:allow :all
                              'js js/globalThis}}))

(sci/alter-var-root sci/print-fn (constantly *print-fn*))
(sci/alter-var-root sci/print-err-fn (constantly *print-err-fn*))

(sci/enable-unrestricted-access!)

(defn init []
  (reset! sci {:resolve-file resolve-file
               :eval-form #(sci/eval-form ctx %)}))
