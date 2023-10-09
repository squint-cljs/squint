(ns squint.compiler.sci
  (:require ["fs" :as fs]
            [squint.compiler.node :as cn :refer [sci]]
            [sci.core :as sci]
            [squint.internal.node.utils :refer [resolve-file]]))

(defn slurp [f]
  (fs/readFileSync f "utf-8"))

(def !cfg (atom nil))

(def classpath-dirs ["." "src"])

(defn with-cp-dirs [cfg]
  (if (contains? cfg :paths)
    cfg
    (assoc cfg :paths classpath-dirs)))

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
                            (when cfg (reset! !cfg (with-cp-dirs cfg)))
                            (sci/eval-form ctx form))}))
