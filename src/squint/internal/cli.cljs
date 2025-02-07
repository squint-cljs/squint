(ns squint.internal.cli
  (:require
   ["fs" :as fs]
   ["path" :as path]
   ["url" :as url]
   [babashka.cli :as cli]
   [shadow.esm :as esm]
   [squint.compiler :as cc]
   [squint.compiler.node :as compiler]
   [squint.repl.node :as repl]
   #_[squint.repl.nrepl-server :as nrepl]
   [squint.internal.node.utils :as utils]
   [clojure.string :as str])
  (:require-macros [squint.resource :refer [version]]))

(defn file-in-output-dir [file paths output-dir]
  (if output-dir
    (path/resolve output-dir
                  (compiler/adjust-file-for-paths file paths))
    file))

(defn resolve-ns [opts in-file x]
  (let [output-dir (:output-dir opts)
        paths (:paths opts)
        in-file-in-output-dir (file-in-output-dir in-file paths output-dir)]
    (when-let [resolved
               (some-> (utils/resolve-file x)
                       (file-in-output-dir paths output-dir)
                       (some->> (path/relative (path/dirname (str in-file-in-output-dir)))))]
      (let [ext (:extension opts ".mjs")
            ext (if (str/starts-with? ext ".")
                  ext
                  (str "." ext))
            ext' (path/extname resolved)
            file (str "./" (str/replace resolved (re-pattern (str ext' "$")) ext))]
        file))))

(defn files-from-path [path]
  (let [files (fs/readdirSync path)]
    (vec (mapcat (fn [f]
                   (let [f (path/resolve path f)]
                     (if (.isDirectory (fs/lstatSync f))
                       (files-from-path f)
                       [f]))) files))))

(defn files-from-paths [paths]
  (vec (mapcat files-from-path paths)))

(defn match-file [x out-path]
  (cond (keyword? x)
        (re-find (re-pattern (str "\\." (name x) "$")) out-path)
        (string? x)
        (re-find (re-pattern x) out-path)))

(defn copy-file [copy-resources path output-dir paths]
  (when copy-resources
    (let [out-file (path/resolve output-dir (compiler/adjust-file-for-paths path paths))
          out-path (path/dirname out-file)]
      (when (some #(match-file % out-file)
                  copy-resources)
        (when-not (fs/existsSync out-path)
          (println "[squint] Creating directory:" out-path)
          (fs/mkdirSync out-path #js {:recursive true}))
        (println "[squint] zCopying resource" path "to" out-path)
        (fs/copyFileSync path out-file)))))

(defn compile-files
  [opts files]
  (let [opts (utils/process-opts! opts)
        paths (:paths opts)
        copy-resources (:copy-resources opts)
        output-dir (:output-dir opts ".")
        files (if (empty? files)
                (files-from-paths paths)
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
--paths: source paths to search for cljs/cljc files
--output-dir: output directory for JS files"))
      (reduce (fn [prev f]
                (-> (js/Promise.resolve prev)
                    (.then
                     #(do
                        (if (contains? #{".cljc" ".cljs"} (path/extname f))
                          (do (println "[squint] Compiling CLJS file:" f)
                              (compiler/compile-file (assoc opts
                                                            :in-file f
                                                            :resolve-ns (fn [x]
                                                                          (resolve-ns opts f x)))))
                          (copy-file copy-resources f output-dir paths))))
                    (.then (fn [{:keys [out-file]}]
                             (when out-file (println "[squint] Wrote file:" out-file))
                             out-file))))
              nil
              files))))

(defn print-help []
  (println (str "Squint v" (version) "

Usage: squint <subcommand> <opts>

Subcommands:

-e           <expr>       Compile and run expression.
run       <file.cljs>     Compile and run a file
watch                     Watch :paths in squint.edn
compile   <file.cljs> ... Compile file(s)
repl                      Start repl
help                      Print this help

Use squint <subcommand> --help to show more info.")))

(defn fallback [{:keys [rest-cmds opts]}]
  (if-let [e (:e opts)]
    (if (:help opts)
      (println "Usage: squint -e <expr> <opts>

Options:

--no-run: do not run compiled expression
--show:   print compiled expression")
      (let [e (if (:repl opts)
                (str/replace "(do %s\n)" "%s" e)
                e)
            res (cc/compile-string e (assoc opts :repl (:repl opts) :ns-state (atom {:current 'user})
                                            :context (if (:repl opts) :return :statement)
                                            :elide-exports (and (:repl opts)
                                                                (not (false? (:elide-exports opts))))))
            res (if (:repl opts)
                  (str/replace "(async function() { %s })()" "%s" res)
                  res)
            dir (fs/mkdtempSync ".tmp")
            f (str dir "/squint.mjs")]
        (fs/writeFileSync f res "utf-8")
        (when (:show opts)
          (println res))
        (when-not (false? (:run opts))
          (let [path (if (path/isAbsolute f) f
                         (path/resolve (js/process.cwd) f))
                path (str (url/pathToFileURL path))]
            (-> (if (:repl opts)
                  (js/Promise.resolve (js/eval res))
                  (esm/dynamic-import path))
                (.then (fn [v]
                         (when (:repl opts)
                           (prn v))))
                (.finally (fn []
                            (fs/rmSync dir #js {:force true :recursive true}))))))))
    (if (or (:help opts)
            (= "help" (first rest-cmds))
            (empty? rest-cmds))
      (print-help)
      (compile-files opts rest-cmds))))

(defn run [{:keys [opts]}]
  (let [opts (utils/process-opts! opts)
        {:keys [file help]} opts]
    (if help
      nil
      (do (println "[squint] Running" file)
          (-> (.then (compiler/compile-file (assoc opts :in-file file :resolve-ns (fn [x]
                                                                                    (resolve-ns opts file x))))
                     (fn [{:keys [out-file]}]
                       (let [path (if (path/isAbsolute out-file) out-file
                                      (path/resolve (js/process.cwd) out-file))
                             path (str (url/pathToFileURL path))]
                         (esm/dynamic-import path)))))))))

#_(defn compile-form [{:keys [opts]}]
    (let [e (:e opts)]
      (println (t/compile! e))))

(defn watch [opts]
  (let [opts (process-opts! opts)
        paths (:paths opts)
        output-dir (:output-dir opts ".")
        copy-resources (:copy-resources opts)]
    (-> (-> (esm/dynamic-import "chokidar")
            (.catch (fn [err]
                      (js/console.error err))))
        (.then (fn [^js lib]
                 (let [watch (.-watch lib)]
                   (println "[squint] Watching paths:" (str/join ", " paths))
                   (doseq [path paths]
                     (.on ^js (watch path) "all"
                          (fn [event path]
                            (when (fs/existsSync path)
                              (when-not (.isDirectory (fs/lstatSync path))
                                (if (and (contains? #{"add" "change"} event)
                                         (contains? #{".cljs" ".cljc"} (path/extname path)))
                                  (-> (compile-files opts [path])
                                      (.catch (fn [e]
                                                (js/console.error e))))
                                  (copy-file copy-resources path output-dir paths)))))))))))))

(defn start-nrepl [{:keys [opts]}]
  (-> (esm/dynamic-import "./node.nrepl_server.js")
      (.then (fn [^js val]
               ((.-startServer val) opts)))))

(def table
  [{:cmds ["run"]        :fn run :args->opts [:file]}
   {:cmds ["compile"]
    :fn (fn [{:keys [rest-cmds opts]}]
          (compile-files opts rest-cmds))}
   {:cmds ["repl"]       :fn repl/repl}

   {:cmds ["socket-repl"]  :fn repl/socket-repl}
   {:cmds ["nrepl-server"] :fn start-nrepl}

   {:cmds ["watch"]      :fn (fn [{:keys [opts]}]
                               (watch opts))}
   {:cmds []             :fn fallback}])

(defn init []
  (cli/dispatch table
                (.slice js/process.argv 2)
                {:aliases {:h :help}
                 :coerce {:elide-exports :boolean
                          :elide-imports :boolean
                          :output-dir    :string
                          :repl          :boolean
                          :paths         [:string]
                          :extension     :string}}))
