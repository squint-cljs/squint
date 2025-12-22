(ns squint.compiler.sci
  (:require
   ["node:fs" :as fs]
   ["node:module" :refer [createRequire]]
   ["node:path" :as path]
   ["node:url" :refer [pathToFileURL]]
   [sci.core :as sci]
   [squint.compiler.node :as cn :refer [sci]]
   [squint.internal.node.utils :refer [resolve-file]]))

(defn slurp [f]
  (fs/readFileSync f "utf-8"))

(def cwd (.-cwd js/process))

(def require-from-cwd
  (createRequire
   (-> (path/join (cwd) "noop.js")
       pathToFileURL
       .-href)))

(declare ctx)
(def ctx (sci/init {:load-fn (fn [{:keys [namespace]}]
                               (if (string? namespace)
                                 (let [mod (require-from-cwd namespace)]
                                   (sci/add-js-lib! ctx namespace mod)
                                   ;; empty map = SCI will take care of aliases, refer, etc.
                                   {})
                                 (when-let [f (resolve-file namespace)]
                                   (let [fstr (slurp f)]
                                     {:source fstr}))))
                    :classes {:allow :all
                              'js js/globalThis}
                    :features #{:cljs}}))

(sci/alter-var-root sci/print-fn (constantly *print-fn*))
(sci/alter-var-root sci/print-err-fn (constantly *print-err-fn*))

(sci/enable-unrestricted-access!)

(defn init []
  (reset! sci {:resolve-file resolve-file
               :eval-form (fn [form _cfg]
                            (sci/eval-form ctx form))}))
