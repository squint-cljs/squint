(ns squint.compiler.node
  (:require
   ["fs" :as fs]
   ["path" :as path]
   [clojure.string :as str]
   [edamame.core :as e]
   [shadow.esm :as esm]
   [squint.compiler :as compiler]
   [squint.internal.node.utils :as utils]))

(def sci (atom nil))

(defn slurp [f]
  (fs/readFileSync f "utf-8"))

(defn spit [f s]
  (fs/writeFileSync f s "utf-8"))

(defn- cljc-with-macros?
  "Check if a require clause refers to a .cljc file that contains defmacro."
  [[libname & _]]
  (when (symbol? libname)
    (when-let [path (utils/resolve-file libname)]
      (and (str/ends-with? path ".cljc")
           (str/includes? (slurp path) "defmacro")))))

(defn scan-macros [s {:keys [ns-state]}]
  (let [maybe-ns (e/parse-next (e/reader s) compiler/squint-parse-opts)]
    (when (and (seq? maybe-ns)
               (= 'ns (first maybe-ns)))
      (let [[_ns the-ns-name & clauses] maybe-ns
            [require-macros reload] (some (fn [[clause reload]]
                                            (when (and (seq? clause)
                                                       (= :require-macros (first clause)))
                                              [(rest clause) reload]))
                                          (partition-all 2 1 clauses))
            ;; also scan :require clauses for .cljc files that may contain macros
            require-cljc (some->> clauses
                                  (some (fn [clause]
                                          (when (and (seq? clause)
                                                     (= :require (first clause)))
                                            (rest clause))))
                                  (filter cljc-with-macros?))
            all-macro-requires (concat require-macros require-cljc)]
        (when (seq all-macro-requires)
          (.then (esm/dynamic-import "./compiler.sci.js")
                 (fn [_]
                   (let [eval-form (:eval-form @sci)]
                     (.then
                      (reduce
                       (fn [prev require-macros]
                         (.then prev
                                (fn [_]
                                  (let [[macro-ns & {:keys [refer as]}] require-macros
                                        macros (js/Promise.resolve
                                                (do (eval-form (cond-> (list 'require (list 'quote macro-ns))
                                                                 reload (concat [:reload])))
                                                    (let [publics (eval-form
                                                                   `(ns-publics '~macro-ns))
                                                          macros (keep (fn [[k v]]
                                                                         (when (:macro (meta v))
                                                                           [k (deref v)])) publics)
                                                          macros (into {} macros)]
                                                      macros)))]
                                    (.then macros
                                           (fn [macros]
                                             (swap! ns-state (fn [ns-state]
                                                               (cond-> (assoc-in ns-state [:macros macro-ns] macros)
                                                                 as (assoc-in [the-ns-name :aliases as] macro-ns)
                                                                 refer (update-in [the-ns-name :refers] merge (zipmap refer (repeat macro-ns))))))))))))
                       (js/Promise.resolve nil)
                       all-macro-requires)
                      (fn [_]
                        ;; register lazy macro resolver for transitive deps
                        (swap! ns-state assoc :resolve-macro
                               (fn [ns-sym name-sym]
                                 (let [fqn (symbol (str ns-sym) (str name-sym))]
                                   (eval-form
                                    (list 'when-let ['v (list 'resolve (list 'quote fqn))]
                                          (list 'when (list ':macro (list 'meta 'v))
                                                (list 'deref 'v)))))))))))))))))

(defn default-ns-state []
  (atom {:current 'user}))

(defn ->opts [opts]
  (assoc opts :ns-state (or (:ns-state opts) (default-ns-state))))

(defn compile-string [contents opts]
  (let [opts (->opts opts)]
    (-> (js/Promise.resolve (scan-macros contents opts))
        (.then #(compiler/compile-string* contents opts)))))

(defn in-dir? [dir file]
  (let [dir (.split ^js (path/resolve dir) path/sep)
        file (.split ^js (path/resolve file) path/sep)]
    (loop [dir dir
           file file]
      (or (empty? dir)
          (and (seq file)
               (= (first dir)
                  (first file))
               (recur (rest dir)
                      (rest file)))))))

(defn adjust-file-for-paths [in-file paths]
  (let [out-file (reduce (fn [acc path]
                           (if (in-dir? path in-file)
                             (reduced (path/relative path in-file))
                             acc))
                         in-file
                         paths)]
    out-file))

(defn file-in-output-dir [file paths output-dir]
  (if output-dir
    (path/resolve output-dir
                  (adjust-file-for-paths file paths))
    file))

(defn resolve-ns
  "Resolve a required libname/ns `x` to a relative path to its compiled output,
  relative to `in-file`'s output location, using the caller-supplied
  `:paths`/`:output-dir`/`:extension`. Returns nil when `x` is not a local
  source ns (e.g. an npm package), leaving it as a bare import."
  [{:keys [output-dir paths extension]} in-file x]
  (let [in-file-in-output-dir (file-in-output-dir in-file paths output-dir)]
    (when-let [resolved (some-> (utils/resolve-file x paths)
                                (file-in-output-dir paths output-dir)
                                (some->> (path/relative (path/dirname (str in-file-in-output-dir)))))]
      (let [ext (or extension ".mjs")
            ext (if (str/starts-with? ext ".") ext (str "." ext))
            ext' (path/extname resolved)]
        (str "./" (str/replace resolved (re-pattern (str ext' "$")) ext))))))

(defn compile-file [{:keys [in-file in-str out-file extension output-dir]
                     :or {output-dir ""}
                     :as opts}]
  (let [contents (or in-str (slurp in-file))
        opts (->opts opts)
        ;; When the caller didn't supply :resolve-ns, wire squint's own
        ;; squint.edn-style resolution so local cross-ns requires compile to a
        ;; relative path (./foo.js) instead of a bare 'foo' a bundler can't
        ;; resolve. Driven by the caller-passed :paths/:output-dir/:extension.
        opts (cond-> opts
               (and in-file (not (:resolve-ns opts)))
               (assoc :resolve-ns (fn [x] (resolve-ns opts in-file x))))]
    (-> (compile-string contents (assoc opts :ns nil))
        (.then (fn [{:keys [javascript jsx] :as opts}]
                 (let [opts (utils/process-opts! opts)
                       paths (:paths opts ["." "src"])
                       out-file (path/resolve output-dir
                                              (or out-file
                                                  (str/replace (adjust-file-for-paths in-file paths) #".clj(s|c)$"
                                                               (if jsx
                                                                 ".jsx"
                                                                 (or (when-let [ext extension]
                                                                       (str "." (str/replace ext #"^\." "")))
                                                                     ".mjs")))))
                       out-path (path/dirname out-file)]
                   (when-not (fs/existsSync out-path)
                     (fs/mkdirSync out-path #js {:recursive true}))
                   (when-not (fs/existsSync out-path)
                     (throw (js/Error. "File not found, make sure output-dir is a valid path: "
                                       {:output-dir output-dir
                                        :out-file out-file})))
                   (spit out-file javascript)
                   (assoc opts :out-file out-file)))))))

(defn ->clj [x]
  (js->clj x :keywordize-keys true))

(defn- jsify [f]
  (fn [& args]
    (let [args (mapv ->clj args)
          ret (apply f args)]
      (if (instance? js/Promise ret)
        (.then ret clj->js)
        (clj->js ret)))))

#_{:clj-kondo/ignore [:unused-private-var]}
(def ^:private compile-string-js
  (jsify compile-string))

#_{:clj-kondo/ignore [:unused-private-var]}
(def ^:private compile-file-js
  (jsify compile-file))
