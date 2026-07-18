(ns squint.compiler.node
  (:require
   ["fs" :as fs]
   ["path" :as path]
   [clojure.string :as str]
   [shadow.esm :as esm]
   [squint.compiler :as compiler]
   [squint.internal.node.macro-scan :as ms]
   [squint.internal.node.utils :as utils]))

(def sci (atom nil))

(defn slurp [f]
  (fs/readFileSync f "utf-8"))

(defn spit [f s]
  (fs/writeFileSync f s "utf-8"))

(def dialect
  {:parse-opts compiler/squint-parse-opts
   :features #{:squint :cljs :default}
   :config-file utils/default-config-file
   :target :squint
   :import-sci (fn [] (esm/dynamic-import "./compiler.sci.js"))
   :sci sci})

(def compile-time-source (partial ms/compile-time-source dialect))

(defn scan-macros [s opts]
  (ms/scan-macros dialect s opts))

(defn default-ns-state []
  (atom {:current 'user}))

(defn ->opts [opts]
  (assoc opts :ns-state (or (:ns-state opts) (default-ns-state))))

(defn compile-string [contents opts]
  (let [opts (->opts opts)]
    (-> (js/Promise.resolve (scan-macros contents opts))
        (.then #(compiler/compile-string* contents opts)))))

(def in-dir? utils/in-dir?)

(def adjust-file-for-paths utils/adjust-file-for-paths)

(def file-in-output-dir utils/file-in-output-dir)

(def compiled-output-path utils/compiled-output-path)

(def resolve-ns utils/resolve-ns)

(def resolve-ns-repl utils/resolve-ns-repl)

(def ^:private dev-hooks utils/dev-hooks)

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
                       ;; Raw JSX tags need a .jsx extension so a downstream
                       ;; transform (e.g. @vitejs/plugin-react) picks them up.
                       ;; With :jsx-runtime the output is plain JS (jsx() calls),
                       ;; so honor the configured extension instead.
                       jsx-tags? (and jsx (not (:jsx-runtime opts)))
                       out-file (path/resolve output-dir
                                              (or out-file
                                                  (str/replace (adjust-file-for-paths in-file paths) #".clj(s|c)$"
                                                               (if jsx-tags?
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
                   (cond-> (assoc opts :out-file out-file)
                     (:repl opts) (assoc :dev-hooks (dev-hooks (:ns-state opts))))))))))

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

#_{:clj-kondo/ignore [:unused-private-var]}
(def ^:private read-config-js
  (jsify utils/read-config))

#_{:clj-kondo/ignore [:unused-private-var]}
(def ^:private deps-paths-js
  (jsify utils/deps-paths))
