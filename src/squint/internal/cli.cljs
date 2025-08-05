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
   [clojure.set :as cset]
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
  (let [e (:e opts)
        e e #_(if (:repl opts)
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
  {:elide-imports  {:desc "Do not include imports"
                    :coerce :boolean}
   :elide-exports  {:desc "Do not include exports"
                    :coerce :boolean}
   :extension      {:desc "Default extension for JS files"
                    :ref "<ext>"
                    :default ".mjs"
                    :coerce :string}
   :paths          {:desc "Search paths for cljs/cljc files"
                    :ref "<path>"
                    :squint-edn? true
                    :coerce [:string]
                    :example-values ["src" "other/src"] }
   :copy-resources {:desc "Copy any non cljs/cljc files found in --paths as resources"
                    :extra-desc ["- use keyword to match files with extension"
                                 "- otherwise matches files by regex"]
                    :ref "<resource>"
                    :coerce [:string]
                    :collect (fn [coll arg-value]
                               (conj (or coll #{})
                                     (if (str/starts-with? arg-value ":")
                                       (cli/coerce arg-value :keyword)
                                       (cli/coerce arg-value :string))))
                    :example-values [:json "'.*/images/.*\\\\.png'"]}
   :output-dir     {:desc "Base output directory for JS files"
                    :ref "<dir>"
                    :default "."
                    :coerce :string}})
(def compile-opt-order [:elide-imports :elide-exports :extension :paths :copy-resources :output-dir])

(def watch-spec (-> compile-spec
                    (assoc-in [:paths :require] true)
                    (assoc-in [:paths :desc] "Watch paths for cljs/cljc files")))
(def watch-opt-order compile-opt-order)

(def eval-spec
  (-> {:e    {:desc "expression to evaluate"
              :ref "<expr>"
              :coerce :string}
       :run  {:desc "run the compiled expression"
              :default true ;; unusual for a switch to be true by default, will be presented as --no-run
              :coerce :boolean}
       :show {:desc "print the compiled expression"
              :coerce :boolean}
       :repl {:desc "evaluate and print the expression"
              :coerce :boolean}}
      (merge (select-keys compile-spec [:elide-imports :elide-exports]))))

(def eval-opt-order [:e :run :show :repl :elide-imports :elide-exports])

(def run-spec (-> compile-spec
                  (dissoc :paths :copy-resources)))
(def run-opt-order [:elide-imports :elide-exports :extension :output-dir])

(def nrepl-server-spec
  {:host {:desc "Host on which to expose server"
          :extra-desc ["Use 0.0.0.0 to allow access from network"]
          :default "127.0.0.1"
          :coerce :string}
   :port {:desc "Port on which to expose server"
          :extra-desc ["Use 0 to randomly select a port"]
          :default 0
          :coerce :long}})
(def nrepl-server-opt-order [:host :port])

(defn kw-opt->cli-opt
  "Squint only technically has long opts, but we'll show a single char opt as short to support -e"
  [kw-opt]
  (let [opt (name kw-opt)]
    (if (= 1 (count opt))
      (str "-" opt)
      (str "--" opt))))

(defn- opts->table
  "Customized bb cli opts->table for squint"
  [{:keys [spec order]}]
  (mapv (fn [[long-opt {:keys [default default-desc desc extra-desc coerce example-values ref require]}]]
          (let [option (kw-opt->cli-opt long-opt)
                option-shown (if (= true default)
                               (str/replace option #"^(-*)(.*)$" "$1no-$2")
                               option)
                default-shown (or default-desc
                                  (when (and (some? default)
                                             (not (boolean? default)))
                                    default))
                attribute (or (when require "*required*")
                              default-shown)
                desc-shown (cond-> [(if attribute
                                      (str desc " [" attribute "]")
                                      desc)]
                             extra-desc (into extra-desc)
                             (= true default) (conj (str "use " option " to enable"))
                             (vector? coerce) (conj "for multiple, repeat, ex:"
                                                    (str/join " " (mapv #(str option " " %) example-values))))]
            [(str option-shown (when ref (str " " ref)))
             (str/join "\n " desc-shown)]))
        (let [order (or order (keys spec))]
          (map (fn [k] [k (spec k)]) order))))

(defn format-opts
  "customized bb cli format-opts for squint"
  [{:as cfg}]
  (cli/format-table {:rows (opts->table cfg) :indent 1}))

(defn error-text [text]
  (if (str/starts-with? "win" js/process.platform)
    text
    (str "\u001B[31m" text "\u001B[0m")))



(defn make-error-fn [cmd-usage-help]
  (fn [{:keys [type cause msg option value] :as data}]
    (if-let [error-msg (case type
                         :org.babashka/cli (cond
                                             (= :require cause)
                                             (str "Missing required option: " (kw-opt->cli-opt option))
                                             ;; Override default of: Coerce failure: "cannot transform (implicit) true to string"
                                             (and (= :coerce cause) (= "true" value))
                                             (str "Option specified without value: " (kw-opt->cli-opt option))
                                             ;; Override default: Report unknown option in cmdline syntax, not as keyword
                                             (= :restrict cause)
                                             (str "Unrecognized option: " (kw-opt->cli-opt option))
                                             :else msg)
                         :squint/cli msg
                         nil)]
      (do (println (str (error-text "ERROR") ": " error-msg "\n\n" cmd-usage-help))
          (.exit js/process 1))
      (throw (ex-info msg data)))))

(defn args-validate [{:keys [error-fn arg-ref arg-count args]}]
  (let [actual-arg-count (count args)]
    (when (not= actual-arg-count arg-count)
      (error-fn
        {:type :squint/cli
         :msg (str "Must specify "
                   (cond (zero? arg-count) "no"
                         (= 1 arg-count) "a single"
                         :else arg-count)
                   " " (or arg-ref "args")
                   (when (seq args)
                     (str ", found: " (str/join " " args))))}))))

(def table
  [{:cmd "run"
    :usage "Usage: squint run <file> [options...]"
    :spec run-spec
    :usage-opt-order run-opt-order
    :squint-edn? true
    :arg-ref "<file>"
    :arg-count 1
    :fn (fn [{:keys [args opts]}]
          (run opts (first args)))}
   {:cmd "compile"
    :usage "Usage: squint compile <file>... [options...]"
    :usage-note "Must specify one of --paths or <files>."
    :squint-edn? true
    :spec compile-spec
    :usage-opt-order compile-opt-order
    :post-validate (fn [{:keys [args opts error-fn]}]
                     (let [files args
                           paths (:paths opts)]
                       (when-not (and (or (seq paths) (seq files))
                                      (not (and (seq paths) (seq files))))
                         (error-fn {:type :squint/cli
                                    :msg "Must specify one of --paths or <files>"}))))
    :fn (fn [{:keys [args opts]}]
          (-> (compile-files opts args)
              (.then (fn [{:keys [compiled copied]}]
                       (println "[squint] Compiled sources:" compiled)
                       (when (:copy-resources opts)
                         (println "[squint] Copied resources:" copied))
                       (when (zero? (+ compiled copied))
                         (println (str "[squint] " (error-text "ERROR") " - Compile processed no files"))
                         (.exit js/process 1))))))}
   {:cmd "repl"
    :arg-count 0
    :fn repl/repl}
   {:cmd "socket-repl"
    :arg-count 0
    :fn repl/socket-repl}
   {:cmd "nrepl-server"
    :usage "Usage: squint nrepl-server [option..]"
    :spec nrepl-server-spec
    :usage-opt-order nrepl-server-opt-order
    :arg-count 0
    :fn start-nrepl}
   {:cmd "watch"
    :usage "Usage: squint watch <files> [options...]"
    :squint-edn? true
    :spec watch-spec
    :usage-opt-order watch-opt-order
    :arg-count 0
    :fn (fn [{:keys [opts]}] (watch opts))}
   {:cmd "eval" ;; expressed as -e on the command line
    :usage "Usage: squint -e <expr> [options...]"
    :artificial true
    :spec eval-spec
    :usage-opt-order eval-opt-order
    :arg-count 0
    :fn evaluate}])

(defn is-valid-cmd [cmd-table cmd]
  (some #(and (= cmd (:cmd %)) (not (:artificial %)))
        cmd-table))

(defn is-any-cmd [cmd-table cmd]
  (some #(= cmd (:cmd %)) cmd-table))

(defn parse-cmd-opts-args
  ([cli-args]
   (parse-cmd-opts-args cli-args {}))
  ([cli-args opts]
   (let [{:keys [args opts]} (cli/parse-args cli-args opts)]
     {:cmd (first args)
      :args (rest args)
      :opts opts})))

(defn support-implicit-compile
  "historically squint has supported the ommission of the compile command"
  [cmd-table cli-args]
  (let [{:keys [cmd opts]} (parse-cmd-opts-args cli-args)]
    (if (and (not (:e opts)) (not (is-valid-cmd cmd-table cmd)))
      (into ["compile"] cli-args)
      cli-args)))

(defn treat-eval-opt-as-command
  "squint expresses eval as option -e but we are otherwise command driven"
  [cmd-table cli-args]
  (let [{:keys [cmd opts]} (parse-cmd-opts-args cli-args)]
    (if (and (:e opts) (not (is-any-cmd cmd-table cmd)))
      (into ["eval"] cli-args)
      cli-args)))

(defn cmds-help-requested [cli-args]
  (let [{:keys [cmd opts]} (parse-cmd-opts-args cli-args {:aliases {:h :help}})]
    (when (or (not (seq cli-args))
              (and (not (seq opts)) (= "help" cmd))
              (and (not cmd) (= {:help true} opts)))
      (str "Squint v" (version) "

Usage: squint <subcommand> [options...]

Subcommands:

-e           <expr>          Compile and run expression.
run          <file.cljs>     Compile and run a file
watch                        Watch and auto-recompile paths
compile      <file.cljs> ... Compile file(s)
repl                         Start repl
nrepl-server                 Start nrepl server
help                         Print this help

Use squint <subcommand> --help to show more info."))))

(defn cmd-def-from-cmd [cmd-table cmd]
  (some #(when (= cmd (:cmd %)) %) cmd-table))

(defn cmd-help-requested [cmd-table cli-args]
  (let [{:keys [cmd opts]} (parse-cmd-opts-args cli-args {:aliases {:h :help}})]
    (when (and (is-any-cmd cmd-table cmd) (:help opts))
      (:usage-help (cmd-def-from-cmd cmd-table cmd)))))

(defn cmd-usage-help [{:keys [spec usage usage-opt-order usage-note squint-edn? cmds]}]
  (let [cmd (first cmds)
        cmd-usage (or usage (str "Usage: squint " cmd "\n\nOptions: none for this command"))
        opts-usage (when usage-opt-order (str "Options:\n\n"
                                              (format-opts {:spec spec :order usage-opt-order})))
        usage-notes (cond-> []
                      squint-edn? (conj "Options are also read from squint.edn, command line options override.")
                      usage-note (conj usage-note))
        help (keep identity [cmd-usage opts-usage (when (seq usage-notes)
                                                    (str/join "\n" usage-notes))])]
    (str/join "\n\n" help)))

(defn cmd-def-from-cli-args [cmd-table cli-args]
  (let [{:keys [cmd]} (parse-cmd-opts-args cli-args)]
    (cmd-def-from-cmd cmd-table cmd)))

(defn check-for-required-options
  "Extracted/adapted from babashka.cli/parse-opts"
  [{:keys [spec opts error-fn]}]
  (let [require (:require (cli/spec->opts spec))]
    (doseq [k require]
      (when-not (find opts k)
        (error-fn {:type :org.babashka/cli
                   :spec spec
                   :cause :require
                   :msg (str "Required option: " k)
                   :require require
                   :option k
                   :opts opts})))))

(defn spec-opt-key [spec op spec-key]
  (let [defer-spec-key (keyword (str "deferred-" (name spec-key)))]
    (reduce-kv (fn [m k v]
                 (assoc m k (cset/rename-keys v (case op
                                                  :defer {spec-key defer-spec-key}
                                                  :enable {defer-spec-key spec-key}))))
               {}
               spec)))

(defn apply-opt-defaults [spec opts]
  (reduce-kv (fn [opts spec-key {:keys [default]}]
               (if (and default (nil? (spec-key opts)))
                 (assoc opts spec-key default)
                 opts))
             opts
             spec))

(defn init []
  (let [cli-args (.slice js/process.argv 2)]
    (if-let [help (cmds-help-requested cli-args)]
      (println help)
      (let [cmd-table (mapv (fn [{:keys [spec] :as d}]
                              (let [usage-help (cmd-usage-help d)]
                                (assoc d
                                       :spec (-> spec
                                                 (spec-opt-key :defer :require)
                                                 (spec-opt-key :defer :default)
                                                 (assoc :help {:alias :h}))
                                       :usage-help usage-help
                                       :error-fn (make-error-fn usage-help)
                                       :restrict true)))
                            table)
            cli-args (->> cli-args
                      (support-implicit-compile cmd-table)
                      (treat-eval-opt-as-command cmd-table))]
        (if-let [help (cmd-help-requested cmd-table cli-args)]
          (println help)
          (let [cmd-def (cmd-def-from-cli-args cmd-table cli-args)
                cmd-opts-args (parse-cmd-opts-args cli-args cmd-def)
                squint-edn? (:squint-edn? cmd-def)
                merged-cmd-opts-args (if-not squint-edn?
                                       cmd-opts-args
                                       (assoc cmd-opts-args
                                              :opts (utils/process-opts! (:opts cmd-opts-args))))
                ;; we separate options validation/defaults from parsing because options can come
                ;; from 2 sources: command-line and squint.edn
                cmd-def (update cmd-def :spec #(-> %
                                                   (spec-opt-key :enable :require)
                                                   (spec-opt-key :enable :default)))
                merged-cmd-opts-args (assoc cmd-opts-args
                                            :opts (apply-opt-defaults (:spec cmd-def)
                                                                      (:opts merged-cmd-opts-args)))]
            (check-for-required-options (assoc cmd-def :opts (:opts merged-cmd-opts-args)))
            (when (:arg-count cmd-def)
              (args-validate (merge cmd-def merged-cmd-opts-args)))
            (when-let [f (:post-validate cmd-def)]
              (f (merge cmd-def merged-cmd-opts-args)))
            ((:fn cmd-def) merged-cmd-opts-args)))))))
