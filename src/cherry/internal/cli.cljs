(ns cherry.internal.cli
  (:require [babashka.cli :as cli]
            [cherry.transpiler :as t]
            [shadow.esm :as esm]
            ))

(defn transpile-files
  [{:keys [rest-cmds]}]
  (doseq [f rest-cmds]
    (println "Transpiling CLJS file:" f)
    (let [{:keys [out-file]} (t/transpile-file {:in-file f})]
      (println "Wrote JS file:" out-file))))

(defn run [{:keys [opts]}]
  (let [{:keys [file]} opts
        {:keys [out-file]} (t/transpile-file {:in-file file})]
    (esm/dynamic-import (str (js/process.cwd) "/" out-file))))

(defn init []
  (cli/dispatch [{:cmds ["run"] :cmds-opts [:file] :fn run}
                 {:cmds ["transpile"] :fn transpile-files}
                 {:cmds [] :fn transpile-files}]
                (.slice js/process.argv 2)))
