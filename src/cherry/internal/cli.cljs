(ns cherry.internal.cli
  (:require
   ["fs" :as fs]
   [babashka.cli :as cli]
   [cherry.compiler :as t]
   [shadow.esm :as esm]))

(defn compile-files
  [files]
  (reduce (fn [prev f]
            (-> (js/Promise.resolve prev)
                (.then
                 #(do
                    (println "[cherry] Compiling CLJS file:" f)
                    (t/transpile-file {:in-file f})))
                (.then (fn [{:keys [out-file]}]
                         (println "[cherry] Wrote JS file:" out-file)
                         out-file))))
          nil
          files))

(defn print-help []
  (println "Cherry v0.0.0

Usage:

run       <file.cljs>     Compile and run a file
compile   <file.cljs> ... Compile file(s)
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
      (compile-files rest-cmds))))

(defn run [{:keys [opts]}]
  (let [{:keys [file]} opts]
    (println "[cherry] Running" file)
    (.then (t/transpile-file {:in-file file})
           (fn [{:keys [out-file]}]
             (esm/dynamic-import (str (js/process.cwd) "/" out-file))))))

#_(defn compile-form [{:keys [opts]}]
    (let [e (:e opts)]
      (println (t/compile! e))))

(def table
  [{:cmds ["run"]        :fn run :cmds-opts [:file]}
   {:cmds ["compile"]    :fn (fn [{:keys [rest-cmds]}]
                             (compile-files rest-cmds))}
   {:cmds []             :fn fallback}])

(defn init []
  (cli/dispatch table
                (.slice js/process.argv 2)
                {:aliases {:h :help}}))
