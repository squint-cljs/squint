(ns cherry.internal.cli
  (:require [babashka.cli :as cli]
            [cherry.transpiler :as t]))

(defn init []
  (let [{:keys [cmds]} (cli/parse-args (.slice js/process.argv 2))]
    (doseq [f cmds]
      (println "Transpiling CLJS file:" f)
      (let [{:keys [out-file]} (t/transpile-file {:in-file f})]
        (println "Wrote JS file:" out-file)))))
