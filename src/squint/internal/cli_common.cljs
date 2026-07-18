(ns squint.internal.cli-common
  "CLI machinery shared between squint and cherry (see cherry ADR 0003).
  Dialect-specific bits come in via a dialect map:

  {:prog           \"squint\"          ;; program name in help
   :log-prefix     \"[squint]\"        ;; line prefix for CLI output
   :config-file    \"squint.edn\"      ;; config file, keys utils/get-cfg
   :compile-file   compiler/compile-file
   :compile-string compiler/compile-string
   :resolve-ns     (fn [opts in-file x] ...)
   :eval-tmp-file  \"squint.mjs\"}     ;; tmp file name for eval"
  (:require
   ["fs" :as fs]
   ["path" :as path]
   ["url" :as url]
   [babashka.cli :as cli]
   [clojure.string :as str]
   [shadow.esm :as esm]
   [squint.internal.node.utils :as utils]))

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

(defn copy-file [{:keys [log-prefix]} copy-resources file output-dir paths]
  (when copy-resources
    (let [out-file (path/resolve output-dir (utils/adjust-file-for-paths file paths))
          out-path (path/dirname out-file)]
      (when (some #(match-file % out-file)
                  copy-resources)
        (when-not (fs/existsSync out-path)
          (println log-prefix "Creating directory:" out-path)
          (fs/mkdirSync out-path #js {:recursive true}))
        (println log-prefix "Copying resource" file "to" out-path)
        (fs/copyFileSync file out-file)
        {:copied-out-file out-file}))))

(defn print-compile-error [{:keys [log-prefix]} f err]
  ;; ex-data :file names a transitively loaded ns when it differs from the
  ;; file being compiled.
  (let [{:keys [file line column]} (ex-data err)
        loc (str (or file f) (when line (str ":" line (when column (str ":" column)))))
        where (if (and file (not= file f)) (str f " (loading " loc ")") loc)]
    (js/console.error (str log-prefix " Error while compiling " where ": " (.-message ^js err)))))

(defn exit-on-error [err]
  ;; fallback for an unexpected error, not a per-file compile error
  (binding [*print-fn* *print-err-fn*]
    (println (str "Error: " (if (instance? js/Error err) (.-message ^js err) err))))
  (cli/*exit-fn* {:exit 1}))

(defn compile-files
  [{:keys [log-prefix compile-file resolve-ns] :as dialect} opts files]
  (let [paths (:paths opts)
        copy-resources (:copy-resources opts)
        output-dir (:output-dir opts ".")
        files (if (empty? files)
                (files-from-paths paths)
                files)
        counts (atom {:compiled 0 :copied 0 :failed 0})]
    (-> (reduce (fn [prev f]
                  (-> (js/Promise.resolve prev)
                      (.then
                       (fn []
                         (if (contains? #{".cljc" ".cljs"} (path/extname f))
                           (do (println log-prefix "Compiling CLJS file:" f)
                               (-> (compile-file (assoc opts
                                                        :in-file f
                                                        :resolve-ns (fn [x]
                                                                      (resolve-ns opts f x))))
                                   ;; report and record the failure, then keep
                                   ;; going so the rest of the files still compile
                                   (.catch (fn [err]
                                             (print-compile-error dialect f err)
                                             (swap! counts update :failed inc)
                                             nil))))
                           (copy-file dialect copy-resources f output-dir paths))))
                      (.then (fn [{:keys [out-file copied-out-file]}]
                               (cond
                                 out-file (do
                                            (swap! counts update :compiled inc)
                                            (println log-prefix "Wrote file:" out-file))
                                 copied-out-file (swap! counts update :copied inc))
                               out-file))))
                nil
                files)
        (.then (fn [] @counts)))))

(defn evaluate [{:keys [compile-string eval-tmp-file]} {:keys [opts]}]
  (let [e (:expr opts)
        res (compile-string e (assoc opts :repl (:repl opts) :ns-state (atom {:current 'user})
                                     :context (if (:repl opts) :return :statement)
                                     ;; explicit --elide-exports wins, else elide only in repl
                                     :elide-exports (:elide-exports opts (:repl opts))))
        res (if (:repl opts)
              (str/replace "(async function() { %s })()" "%s" res)
              res)
        dir (fs/mkdtempSync ".tmp")
        f (str dir "/" eval-tmp-file)]
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

(defn run-file [{:keys [log-prefix compile-file resolve-ns] :as dialect} opts file]
  (println log-prefix "Running" file)
  (-> (compile-file (assoc opts :in-file file :resolve-ns (fn [x]
                                                            (resolve-ns opts file x))))
      (.catch (fn [err] (print-compile-error dialect file err) (cli/*exit-fn* {:exit 1})))
      (.then (fn [{:keys [out-file]}]
               (when out-file
                 (let [path (if (path/isAbsolute out-file) out-file
                                (path/resolve (js/process.cwd) out-file))
                       path (str (url/pathToFileURL path))]
                   (esm/dynamic-import path)))))))

(defn watch [{:keys [log-prefix] :as dialect} opts]
  (let [paths (:paths opts)
        output-dir (:output-dir opts ".")
        copy-resources (:copy-resources opts)]
    (-> (-> (esm/dynamic-import "chokidar")
            (.catch (fn [err]
                      (js/console.error err))))
        (.then (fn [^js lib]
                 (let [watch (.-watch lib)]
                   (println log-prefix "Watching paths:" (str/join ", " paths))
                   (doseq [path paths]
                     (.on ^js (watch path) "all"
                          (fn [event path]
                            (when (fs/existsSync path)
                              (when-not (.isDirectory (fs/lstatSync path))
                                (if (and (contains? #{"add" "change"} event)
                                         (contains? #{".cljs" ".cljc"} (path/extname path)))
                                  (-> (compile-files dialect opts [path])
                                      ;; compile-files already reported per-file
                                      ;; errors; surface only an unexpected one.
                                      (.catch (fn [e]
                                                (js/console.error e))))
                                  (copy-file dialect copy-resources path output-dir paths)))))))))))))

(defn err-exit!
  "Print a standard `Error: <msg>` to stderr (matches babashka.cli) and exit 1
  via cli/*exit-fn*. For app-level errors (arg-count, no files compiled), which
  happen inside a command :fn, after dispatch, so they can't go through
  dispatch's :error-fn."
  [msg]
  (binding [*print-fn* *print-err-fn*]
    (println (str "Error: " msg)))
  (cli/*exit-fn* {:exit 1}))

(defn args-validate
  "Exact positional arg counts, which cli dispatch does not enforce. Called
  from each :fn."
  [{:keys [arg-ref arg-count args]}]
  (when (not= (count args) arg-count)
    (err-exit! (str "Must specify "
                    (cond (zero? arg-count) "no"
                          (= 1 arg-count) "a single"
                          :else arg-count)
                    " " (or arg-ref "args")
                    (when (seq args)
                      (str ", found: " (str/join " " args)))))))

(defn config-note [{:keys [config-file]}]
  (str "Options are also read from " config-file ", command line options override."))

(def compile-spec
  {;; positional <files>, folded in via :args->opts (repeat :file); :coerce []
   ;; collects them; kept out of *-opt-order so it is not shown as an option
   :file           {:coerce []}
   :elide-imports  {:desc "Do not include imports"
                    :coerce :boolean}
   :elide-exports  {:desc "Do not include exports"
                    :coerce :boolean}
   :extension      {:desc "Default extension for JS files"
                    :ref "<ext>"
                    :default ".mjs"
                    :coerce :string}
   :paths          {:desc "Search paths for cljs/cljc files"
                    :ref "<path>"
                    :coerce [:string]
                    :default-desc "., src"
                    :default ["." "src"]}
   :copy-resources {:desc "Copy non cljs/cljc files from --paths as resources; a keyword matches by extension, otherwise by regex (repeat for multiple, e.g. --copy-resources :json)"
                    :ref "<resource>"
                    :coerce [:string]
                    :collect (fn [coll arg-value]
                               (conj (or coll #{})
                                     (if (str/starts-with? arg-value ":")
                                       (cli/coerce arg-value :keyword)
                                       (cli/coerce arg-value :string))))}
   :output-dir     {:desc "Base output directory for JS files"
                    :ref "<dir>"
                    :default "."
                    :coerce :string}})
(def compile-opt-order [:elide-imports :elide-exports :extension :paths :copy-resources :output-dir :help])

(def watch-spec (assoc-in compile-spec [:paths :desc] "Watch paths for cljs/cljc files"))
(def watch-opt-order compile-opt-order)

(def eval-spec
  (-> {;; <expr> positional (via :args->opts), also reachable as the -e top-level
       ;; shorthand (alias). Kept out of eval-opt-order so it is not listed as an
       ;; option - it shows only as the <expr> positional in the usage line.
       :expr {:coerce :string :alias :e}
       :run  {:desc "run the compiled expression"
              :default true ;; unusual for a switch to be true by default, shown as --[no-]run
              :negatable true
              :coerce :boolean}
       :show {:desc "print the compiled expression"
              :coerce :boolean}
       :repl {:desc "evaluate and print the expression"
              :coerce :boolean}}
      (merge (select-keys compile-spec [:elide-imports :elide-exports]))))

(def eval-opt-order [:run :show :repl :elide-imports :elide-exports :help])

(def run-spec (-> compile-spec
                  (dissoc :paths :copy-resources)))
(def run-opt-order [:elide-imports :elide-exports :extension :output-dir :help])

(defn run-cmd [{:keys [config-file] :as dialect}]
  {:cmds ["run"]
   :doc "Compile and run a file."
   :spec run-spec
   :order run-opt-order
   ;; (repeat :file) folds the positional into :file so options after it still parse
   :args->opts (repeat :file)
   :epilog (config-note dialect)
   :fn (fn [{:keys [opts]}]
         (args-validate {:arg-count 1 :arg-ref "<file>" :args (:file opts)})
         (let [opts (utils/expand-paths opts)]
           (utils/set-cfg! config-file opts)
           (run-file dialect opts (first (:file opts)))))})

(defn compile-cmd [{:keys [log-prefix config-file] :as dialect}]
  {:cmds ["compile"]
   :doc "Compile file(s)."
   :spec compile-spec
   :order compile-opt-order
   :args->opts (repeat :file)
   :epilog (str "<files> overrides --paths (and :paths in " config-file ").\n"
                (config-note dialect))
   :fn (fn [{:keys [opts]}]
         (let [opts (utils/expand-paths opts)]
           (utils/set-cfg! config-file opts)
           (-> (compile-files dialect opts (:file opts))
               (.then (fn [{:keys [compiled copied failed]}]
                        (println log-prefix "Compiled sources:" compiled)
                        (when (:copy-resources opts)
                          (println log-prefix "Copied resources:" copied))
                        (cond
                          (pos? failed) (cli/*exit-fn* {:exit 1})
                          (zero? (+ compiled copied))
                          (err-exit! "Compile processed no files"))))
               (.catch exit-on-error))))})

(defn watch-cmd [{:keys [config-file] :as dialect}]
  {:cmds ["watch"]
   :doc "Watch and auto-recompile paths."
   :spec watch-spec
   :order watch-opt-order
   :epilog (config-note dialect)
   :fn (fn [{:keys [opts] :as m}]
         (args-validate (assoc m :arg-count 0))
         (let [opts (utils/expand-paths opts)]
           (utils/set-cfg! config-file opts)
           (watch dialect opts)))})

(def nrepl-server-spec
  {:host {:desc "Host on which to expose server (0.0.0.0 to allow network access)"
          :ref "<host>"
          :default "127.0.0.1"
          :coerce :string}
   :port {:desc "Port on which to expose server (0 to pick a random port)"
          :ref "<port>"
          :default 0
          :coerce :long}})
(def nrepl-server-opt-order [:host :port :help])

(defn nrepl-server-cmd [_dialect]
  {:cmds ["nrepl-server"]
   :doc "Start an nREPL server."
   :spec nrepl-server-spec
   :order nrepl-server-opt-order
   ;; the module lives next to cli.js in the dialect's lib dir
   :fn (fn [{:keys [opts] :as m}]
         (args-validate (assoc m :arg-count 0))
         (-> (esm/dynamic-import "./node.nrepl_server.js")
             (.then (fn [^js val]
                      ((.-startServer val) opts)))))})

(defn eval-cmd [dialect]
  {:cmds ["eval"] ;; also reachable as the top-level -e shorthand
   :doc "Compile and run an expression (also: -e <expr>)."
   :spec eval-spec
   :order eval-opt-order
   :args->opts [:expr]
   :fn (fn [{:keys [args opts] :as m}]
         (args-validate {:arg-count 1 :arg-ref "<expr>"
                         :args (if-some [e (:expr opts)] (cons e args) args)})
         (evaluate dialect m))})

(defn known-cmd? [cmd-table cmd]
  (some #(= [cmd] (:cmds %)) cmd-table))

(defn parse-cmd-opts-args [cli-args]
  (let [{:keys [args opts]} (cli/parse-args cli-args {:aliases {:h :help}})]
    {:cmd (first args)
     :args (rest args)
     :opts opts}))

(defn support-implicit-compile
  "historically squint has supported the omission of the compile command"
  [cmd-table cli-args]
  (let [{:keys [cmd opts]} (parse-cmd-opts-args cli-args)]
    ;; cmd guard: bare --help (no positional cmd) falls through to top-level help.
    ;; :e guard: `-e <expr>` is handled by treat-eval-opt-as-command, not compile.
    (if (and cmd (not (:e opts)) (not (known-cmd? cmd-table cmd)))
      (into ["compile"] cli-args)
      cli-args)))

(defn treat-eval-opt-as-command
  "eval is also expressed as option -e but we are otherwise command driven"
  [cmd-table cli-args]
  (let [{:keys [cmd opts]} (parse-cmd-opts-args cli-args)]
    (if (and (:e opts) (not (known-cmd? cmd-table cmd)))
      (into ["eval"] cli-args)
      cli-args)))

(defn init [{:keys [prog config-file]} table]
  (let [cli-args (vec (.slice js/process.argv 2))
        ;; pass the babashka.cli completion command through untouched: the implicit
        ;; rewrites below would otherwise hide it from dispatch
        completion? (= "org.babashka.cli/completions" (first cli-args))
        cli-args (if completion?
                   cli-args
                   (->> cli-args
                        (support-implicit-compile table)
                        (treat-eval-opt-as-command table)))
        ;; bare invocation -> top-level help
        cli-args (if (and (not completion?) (empty? cli-args)) ["--help"] cli-args)]
    (cli/dispatch table cli-args
                  {:prog prog
                   :help true
                   :restrict true
                   ;; the config file supplies defaults; command-line overrides them
                   :exec-args (utils/get-cfg config-file)})))
