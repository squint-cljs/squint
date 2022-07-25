(ns cherry.internal.cli
  (:require
   ["fs" :as fs]
   [babashka.cli :as cli]
   [cherry.transpiler :as t]
   [shadow.esm :as esm]))

(defn transpile-files
  [files]
  (doseq [f files]
    (println "Transpiling CLJS file:" f)
    (let [{:keys [out-file]} (t/transpile-file {:in-file f})]
      (println "Wrote JS file:" out-file))))

(defn print-help []
  (println "Cherry v0.0.0

Usage:

run       <file.cljs>     Transpile and run a file
transpile <file.cljs> ... Transpile file(s)
help                      Print this help"))

(defn fallback [{:keys [rest-cmds opts]}]
  (if-let [e (:e opts)]
    (let [res (t/transpile-string e)
          dir (fs/mkdtempSync ".tmp")
          f (str dir "/cherry.mjs")]
      (fs/writeFileSync f res "utf-8")
      (when (:show opts)
        (println res))
      (-> (esm/dynamic-import (str (js/process.cwd) "/" f))
          (.finally (fn [_]
                   (fs/rmSync dir #js {:force true :recursive true})))))
    (if (or (:help opts)
            (= "help" (first rest-cmds))
            (empty? rest-cmds))
      (print-help)
      (transpile-files rest-cmds))))

(defn run [{:keys [opts]}]
  (let [{:keys [file]} opts
        {:keys [out-file]} (t/transpile-file {:in-file file})]
    (esm/dynamic-import (str (js/process.cwd) "/" out-file))))

#_(defn compile-form [{:keys [opts]}]
  (let [e (:e opts)]
    (println (t/compile! e))))

(def table
  [{:cmds ["run"]        :fn run :cmds-opts [:file]}
   {:cmds ["transpile"]  :fn (fn [{:keys [rest-cmds]}]
                               (transpile-files rest-cmds))}
   #_{:cmds ["compile"]    :fn compile-form}
   {:cmds []             :fn fallback}])

(defn init []
  (cli/dispatch table
                (.slice js/process.argv 2)
                {:aliases {:h :help}}))
