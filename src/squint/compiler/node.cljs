(ns squint.compiler.node
  (:require
   ["fs" :as fs]
   ["path" :as path]
   #_[sci.core :as sci]
   [clojure.string :as str]
   [edamame.core :as e]
   [shadow.esm :as esm]
   [squint.internal.node.utils :as utils]
   [squint.compiler :as compiler]))

(def sci (atom nil))

(defn slurp [f]
  (fs/readFileSync f "utf-8"))

(defn spit [f s]
  (fs/writeFileSync f s "utf-8"))

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

(defn files-from-path [path]
  (let [files (fs/readdirSync path)]
    (vec (mapcat (fn [f]
                   (let [f (path/resolve path f)]
                     (if (.isDirectory (fs/lstatSync f))
                       (files-from-path f)
                       [f]))) files))))

(defn files-from-paths [paths]
  (vec (mapcat files-from-path paths)))

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

(defn scan-macros [s {:keys [ns-state]}]
  (let [maybe-ns (e/parse-next (e/reader s) compiler/squint-parse-opts)]
    (when (and (seq? maybe-ns)
               (= 'ns (first maybe-ns)))
      (let [[_ns the-ns-name & clauses] maybe-ns
            [require-macros reload] (some (fn [[clause reload]]
                                            (when (and (seq? clause)
                                                       (= :require-macros (first clause)))
                                              [(rest clause) reload]))
                                          (partition-all 2 1 clauses))]
        (when require-macros
          (.then (esm/dynamic-import "./compiler.sci.js")
                 (fn [_]
                   (let [eval-form (:eval-form @sci)]
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
                                                         ks (keys publics)
                                                         vs (vals publics)
                                                         vs (map deref vs)
                                                         publics (zipmap ks vs)]
                                                     publics)))]
                                   (.then macros
                                          (fn [macros]
                                            (swap! ns-state (fn [ns-state]
                                                              (cond-> (assoc-in ns-state [:macros macro-ns] macros)
                                                                as (assoc-in [the-ns-name :aliases as] macro-ns)
                                                                refer (assoc-in [the-ns-name :refers] (zipmap refer (repeat macro-ns))))))
                                            #_(set! compiler/built-in-macros
                                                    ;; hack
                                                    (assoc compiler/built-in-macros macro-ns macros))))))))
                      (js/Promise.resolve nil)
                      require-macros)))))))))

(defn default-ns-state []
  (atom {:current 'user}))

(defn ->opts [opts]
  (assoc opts :ns-state (or (:ns-state opts) (default-ns-state))))

(defn compile-string [contents opts]
  (let [opts (->opts opts)
        resolve-ns (or (:resolve-ns opts)
                       (when-let [if (:in-file opts)]
                         (fn [x]
                           (let [resolved (resolve-ns opts if x)]
                             (js/console.log "resolve-ns" "in-file" if "->" resolved)
                             (str/replace resolved ".mjs" ".cljs")))))
        opts (assoc opts :resolve-ns resolve-ns)]
    (-> (js/Promise.resolve (scan-macros contents opts))
        (.then #(compiler/compile-string* contents opts)))))

(defn compile-file [{:keys [in-file in-str out-file extension output-dir resolve-ns]
                     :or {output-dir ""}
                     :as opts}]
  (let [contents (or in-str (slurp in-file))
        opts (->opts opts)
        resolve-ns (or resolve-ns (fn [x](resolve-ns opts in-file x)))
        opts (assoc opts :resolve-ns resolve-ns)]
    (-> (compile-string contents opts)
        (.then (fn [{:keys [javascript jsx] :as opts}]
                 (let [paths (:paths @utils/!cfg ["." "src"])
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
                     (throw (js/Error. (str "File not found, make sure output-dir is a valid path: ")
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
