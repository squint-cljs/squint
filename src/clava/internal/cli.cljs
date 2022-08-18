(ns clava.internal.cli
  (:require
   ["fs" :as fs]
   ["path" :as path]
   [babashka.cli :as cli]
   [clava.compiler :as cc]
   [clava.compiler.node :as compiler]
   [shadow.esm :as esm]))

(defn compile-files
  [files]
  (reduce (fn [prev f]
            (-> (js/Promise.resolve prev)
                (.then
                 #(do
                    (println "[clava] Compiling CLJS file:" f)
                    (compiler/compile-file {:in-file f})))
                (.then (fn [{:keys [out-file]}]
                         (println "[clava] Wrote JS file:" out-file)
                         out-file))))
          nil
          files))

(defn print-help []
  (println "Clava v0.0.0

Usage:

run       <file.cljs>     Compile and run a file
compile   <file.cljs> ... Compile file(s)
help                      Print this help"))

(defn fallback [{:keys [rest-cmds opts]}]
  (if-let [e (:e opts)]
    (let [res (cc/compile-string e)
          dir (fs/mkdtempSync ".tmp")
          f (str dir "/clava.mjs")]
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
    (println "[clava] Running" file)
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
