(ns squint.internal.cli
  (:require
   ["fs" :as fs]
   ["path" :as path]
   ["glob" :as glob]
   [babashka.cli :as cli]
   [shadow.esm :as esm]
   [squint.compiler :as cc]
   [squint.compiler.node :as compiler]
   [squint.repl.node :as repl]
   [squint.internal.node.utils :as utils]
   [clojure.string :as str]))

(defn resolve-ns [opts in-file x]
  (when-let [resolved
             (some->> (utils/resolve-file x)
                      (path/relative (path/dirname (str in-file))))]
    (let [ext (:extension opts ".mjs")
          ext (if (str/starts-with? ext ".")
                ext
                (str "." ext))
          ext' (path/extname resolved)]
      (str "./" (str/replace resolved (re-pattern (str ext' "$")) ext)))))

(defn glob-cljs-files [dir]
  (glob/globSync (str dir "/**/*.{cljs,cljc}")))

(defn files-from-paths [paths]
  (mapcat glob-cljs-files paths))

(defn compile-files
  [opts files]
  (let [cfg @utils/!cfg
        opts (merge cfg opts)
        files (if (empty? files)
                (files-from-paths (:paths cfg))
                files)]
    ;; shouldn't need this if :coerce worked in babashka.cli
    (when-let [out-dir (:output-dir opts)]
      (when-not (string? out-dir)
        (throw (js/Error. "output-dir must be a string"))))
    (if (:help opts)
      (do (println "Usage: squint compile <files> <opts>")
          (println)
          (println "Options:

--elide-imports: do not include imports
--elide-exports: do not include exports
--extension: default extension for JS files
--output-dir: output directory for JS files"))
      (reduce (fn [prev f]
                (-> (js/Promise.resolve prev)
                    (.then
                     #(do
                        (println "[squint] Compiling CLJS file:" f)
                        (compiler/compile-file (assoc opts
                                                      :in-file f
                                                      :resolve-ns (fn [x]
                                                                    (resolve-ns opts f x))))))
                    (.then (fn [{:keys [out-file]}]
                             (println "[squint] Wrote JS file:" out-file)
                             out-file))))
              nil
              files))))

(defn print-help []
  (println "Squint v0.0.0

Usage: squint <subcommand> <opts>

Subcommands:

-e           <expr>  Compile and run expression.
run       <file.cljs>     Compile and run a file
watch                     Watch :paths in squint.edn
compile   <file.cljs> ... Compile file(s)
repl                      Start repl
help                      Print this help

Use squint <subcommand> --help to show more info."))

(defn fallback [{:keys [rest-cmds opts]}]
  (if-let [e (:e opts)]
    (if (:help opts)
      (println "Usage: squint -e <expr> <opts>

Options:

--no-run: do not run compiled expression
--show:   print compiled expression")
      (let [res (cc/compile-string e (assoc opts :ns-state (atom {:current 'user})))
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
                            (fs/rmSync dir #js {:force true :recursive true}))))))))
    (if (or (:help opts)
            (= "help" (first rest-cmds))
            (empty? rest-cmds))
      (print-help)
      (compile-files opts rest-cmds))))

(defn run [{:keys [opts]}]
  (let [cfg @utils/!cfg
        opts (merge cfg opts)
        {:keys [file help]} opts]
    (if help
      nil
      (do (println "[squint] Running" file)
          (-> (.then (compiler/compile-file (assoc opts :in-file file :resolve-ns (fn [x]
                                                                                    (resolve-ns opts file x))))
                     (fn [{:keys [out-file]}]
                       (let [path (if (path/isAbsolute out-file) out-file
                                      (str (js/process.cwd) "/" out-file))]
                         (esm/dynamic-import path)))))))))

#_(defn compile-form [{:keys [opts]}]
    (let [e (:e opts)]
      (println (t/compile! e))))

(defn watch [opts]
  (let [cfg @utils/!cfg
        opts (merge cfg opts)
        paths (:paths cfg)]
    (-> (-> (esm/dynamic-import "chokidar")
            (.catch (fn [err]
                      (js/console.error err))))
        (.then (fn [^js lib]
                 (let [watch (.-watch lib)]
                   (println "[squint] Watching paths:" (str/join ", " paths))
                   (doseq [path paths]
                     (.on ^js (watch path) "all"
                          (fn [event path]
                            (when (and (contains? #{"add" "change"} event)
                                       (contains? #{".cljs" ".cljc"} (path/extname path)))
                              (-> (compile-files opts [path])
                                  (.catch (fn [e]
                                            (js/console.error e))))))))))))))

(def table
  [{:cmds ["run"]        :fn run :cmds-opts [:file]}
   {:cmds ["compile"]
    :coerce {:elide-exports :boolean
             :elide-imports :boolean
             :output-dir :string}
    :fn (fn [{:keys [rest-cmds opts]}]
          (compile-files opts rest-cmds))}
   {:cmds ["repl"]       :fn repl/repl}
   {:cmds ["watch"]      :fn watch}
   {:cmds []             :fn fallback}])

(defn init []
  (cli/dispatch table
                (.slice js/process.argv 2)
                {:aliases {:h :help}}))
