(ns squint.internal.cli
  (:require
   ["fs" :as fs]
   ["path" :as path]
   [babashka.cli :as cli]
   [shadow.esm :as esm]
   [squint.compiler :as cc]
   [squint.compiler.node :as compiler]))

(defn compile-files
  [files]
  (reduce (fn [prev f]
            (-> (js/Promise.resolve prev)
                (.then
                 #(do
                    (println "[squint] Compiling CLJS file:" f)
                    (compiler/compile-file {:in-file f})))
                (.then (fn [{:keys [out-file]}]
                         (println "[squint] Wrote JS file:" out-file)
                         out-file))))
          nil
          files))

(defn print-help []
  (println "Squint v0.0.0

Usage:

run       <file.cljs>     Compile and run a file
compile   <file.cljs> ... Compile file(s)
help                      Print this help"))

(defn fallback [{:keys [rest-cmds opts]}]
  (if-let [e (:e opts)]
    (let [res (cc/compile-string e)
          dir (fs/mkdtempSync ".tmp")
          f (str dir "/squint.mjs")]
      (fs/writeFileSync f res "utf-8")
      (when (:show opts)
        (println res))
      (when-not (:no-run opts)
        (let [path (if (path/isAbsolute f) f
                       (str (js/process.cwd) "/" f))]
          (-> (esm/dynamic-import path)
              (.finally (fn [_]
                          (fs/rmSync dir #js {:force true :recursive true})))))))
    (if (or (:help opts)
            (= "help" (first rest-cmds))
            (empty? rest-cmds))
      (print-help)
      (compile-files rest-cmds))))

(defn run [{:keys [opts]}]
  (let [{:keys [file]} opts]
    (println "[squint] Running" file)
    (.then (compiler/compile-file {:in-file file})
           (fn [{:keys [out-file]}]
             (let [path (if (path/isAbsolute out-file) out-file
                            (str (js/process.cwd) "/" out-file))]
               (esm/dynamic-import path))))))

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
