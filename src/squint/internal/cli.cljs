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
   [clojure.string :as str]))

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
        (println "[squint] Copying resource" path "to" out-path)
        (fs/copyFileSync path out-file)
        {:copied-out-file out-file}))))

(defn compile-files
  [opts files]
  (let [paths (:paths opts)
        copy-resources (:copy-resources opts)
        output-dir (:output-dir opts ".")
        files (if (empty? files)
                (files-from-paths paths)
                files)
        counts (atom {:compiled 0 :copied 0})]
    (-> (reduce (fn [prev f]
                  (-> (js/Promise.resolve prev)
                      (.then
                        (fn []
                          (if (contains? #{".cljc" ".cljs"} (path/extname f))
                            (do (println "[squint] Compiling CLJS file:" f)
                                (compiler/compile-file (assoc opts
                                                              :in-file f
                                                              :resolve-ns (fn [x]
                                                                            (resolve-ns opts f x)))))
                            (copy-file copy-resources f output-dir paths))))
                      (.then (fn [{:keys [out-file copied-out-file]}]
                               (cond
                                 out-file (do
                                            (swap! counts update :compiled inc)
                                            (println "[squint] Wrote file:" out-file))
                                 copied-out-file (swap! counts update :copied inc))
                               out-file))))
                nil
                files)
        (.then (fn [] @counts)))))

(defn evaluate [{:keys [opts]}]
  (let [e (:expr opts)
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

(defn run [opts file]
  (println "[squint] Running" file)
  (.then (compiler/compile-file (assoc opts :in-file file :resolve-ns (fn [x]
                                                                        (resolve-ns opts file x))))
         (fn [{:keys [out-file]}]
           (let [path (if (path/isAbsolute out-file) out-file
                          (path/resolve (js/process.cwd) out-file))
                 path (str (url/pathToFileURL path))]
             (esm/dynamic-import path)))))

#_(defn compile-form [{:keys [opts]}]
    (let [e (:e opts)]
      (println (t/compile! e))))

(defn watch [opts]
  (let [paths (:paths opts)
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

(def nrepl-server-spec
  {:host {:desc "Host on which to expose server (0.0.0.0 to allow network access)"
          :default "127.0.0.1"
          :coerce :string}
   :port {:desc "Port on which to expose server (0 to pick a random port)"
          :default 0
          :coerce :long}})
(def nrepl-server-opt-order [:host :port :help])

(defn err-exit!
  "Print a standard `Error: <msg>` to stderr (matches babashka.cli) and exit 1 via cli/*exit-fn*.
  For squint's app-level errors (arg-count, no files compiled), which happen inside
  a command :fn, after dispatch, so they can't go through dispatch's :error-fn."
  [msg]
  (binding [*print-fn* *print-err-fn*]
    (println (str "Error: " msg)))
  (cli/*exit-fn* {:exit 1}))

(defn args-validate
  "Squint enforces exact positional arg counts; cli dispatch does not. Called from each :fn."
  [{:keys [arg-ref arg-count args]}]
  (when (not= (count args) arg-count)
    (err-exit! (str "Must specify "
                    (cond (zero? arg-count) "no"
                          (= 1 arg-count) "a single"
                          :else arg-count)
                    " " (or arg-ref "args")
                    (when (seq args)
                      (str ", found: " (str/join " " args)))))))

(def squint-edn-note
  "Options are also read from squint.edn, command line options override.")

(def table
  [{:cmds ["run"]
    :doc "Compile and run a file."
    :spec run-spec
    :order run-opt-order
    ;; (repeat :file) folds the positional into :file so options after it still parse
    :args->opts (repeat :file)
    :epilog squint-edn-note
    :fn (fn [{:keys [opts]}]
          (args-validate {:arg-count 1 :arg-ref "<file>" :args (:file opts)})
          (utils/set-cfg! opts)
          (run opts (first (:file opts))))}
   {:cmds ["compile"]
    :doc "Compile file(s)."
    :spec compile-spec
    :order compile-opt-order
    :args->opts (repeat :file)
    :epilog (str "<files> overrides --paths (and :paths in squint.edn).\n" squint-edn-note)
    :fn (fn [{:keys [opts]}]
          (utils/set-cfg! opts)
          (-> (compile-files opts (:file opts))
              (.then (fn [{:keys [compiled copied]}]
                       (println "[squint] Compiled sources:" compiled)
                       (when (:copy-resources opts)
                         (println "[squint] Copied resources:" copied))
                       (when (zero? (+ compiled copied))
                         (err-exit! "Compile processed no files"))))))}
   {:cmds ["watch"]
    :doc "Watch and auto-recompile paths."
    :spec watch-spec
    :order watch-opt-order
    :epilog squint-edn-note
    :fn (fn [{:keys [opts] :as m}]
          (args-validate (assoc m :arg-count 0))
          (utils/set-cfg! opts)
          (watch opts))}
   {:cmds ["repl"]
    :doc "Start a REPL."
    :fn (fn [m] (args-validate (assoc m :arg-count 0)) (repl/repl m))}
   {:cmds ["socket-repl"]
    :doc "Start a socket REPL."
    :fn (fn [m] (args-validate (assoc m :arg-count 0)) (repl/socket-repl m))}
   {:cmds ["nrepl-server"]
    :doc "Start an nREPL server."
    :spec nrepl-server-spec
    :order nrepl-server-opt-order
    :fn (fn [m] (args-validate (assoc m :arg-count 0)) (start-nrepl m))}
   {:cmds ["eval"] ;; also reachable as the top-level -e shorthand
    :doc "Compile and run an expression (also: -e <expr>)."
    :spec eval-spec
    :order eval-opt-order
    :args->opts [:expr]
    :fn (fn [{:keys [args opts] :as m}]
          (args-validate {:arg-count 1 :arg-ref "<expr>"
                          :args (if-some [e (:expr opts)] (cons e args) args)})
          (evaluate m))}])

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
  "squint expresses eval as option -e but we are otherwise command driven"
  [cmd-table cli-args]
  (let [{:keys [cmd opts]} (parse-cmd-opts-args cli-args)]
    (if (and (:e opts) (not (known-cmd? cmd-table cmd)))
      (into ["eval"] cli-args)
      cli-args)))

(defn init []
  (let [cli-args (vec (.slice js/process.argv 2))
        cli-args (->> cli-args
                      (support-implicit-compile table)
                      (treat-eval-opt-as-command table))
        ;; bare invocation -> top-level help
        cli-args (if (empty? cli-args) ["--help"] cli-args)]
    (cli/dispatch table cli-args
                  {:prog "squint"
                   :help true
                   :restrict true
                   ;; squint.edn supplies defaults; command-line overrides them
                   :exec-args (utils/get-cfg)})))
