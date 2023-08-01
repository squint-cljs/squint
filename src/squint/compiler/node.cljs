(ns squint.compiler.node
  (:require
   ["fs" :as fs]
   ["path" :as path]
   [squint.compiler :as compiler]
   [clojure.string :as str]
   [edamame.core :as e]
   [sci.core :as sci]))

(defn slurp [f]
  (fs/readFileSync f "utf-8"))

(defn spit [f s]
  (fs/writeFileSync f s "utf-8"))

(def classpath-dirs ["." "src"])

(defn resolve-file* [dir munged-macro-ns]
  (let [exts ["cljc" "cljs"]]
    (some (fn [ext]
            (let [full-path (path/resolve dir (str munged-macro-ns "." ext))]
              (when (fs/existsSync full-path)
                full-path)))
          exts)))

(defn resolve-file [macro-ns]
  (let [path (-> macro-ns str (str/replace "-" "_"))]
    (some (fn [dir]
            (resolve-file* dir path))
          classpath-dirs)))

(def ctx (sci/init {:load-fn (fn [{:keys [namespace]}]
                               (let [f (resolve-file namespace)
                                     fstr (slurp f)]
                                 {:source fstr}))
                    :classes {:allow :all
                              'js js/globalThis}}))

(sci/alter-var-root sci/print-fn (constantly *print-fn*))
(sci/alter-var-root sci/print-err-fn (constantly *print-err-fn*))

(sci/enable-unrestricted-access!)

(defn scan-macros [s]
  (let [maybe-ns (e/parse-next (e/reader s) compiler/squint-parse-opts)]
    (when (and (seq? maybe-ns)
               (= 'ns (first maybe-ns)))
      (let [[_ns _name & clauses] maybe-ns
            [require-macros reload] (some (fn [[clause reload]]
                                            (when (and (seq? clause)
                                                       (= :require-macros (first clause)))
                                              [(rest clause) reload]))
                                          (partition-all 2 1 clauses))]
        (when require-macros
          (reduce (fn [prev require-macros]
                    (.then prev
                           (fn [_]
                             (let [[macro-ns & {:keys [refer]}] require-macros
                                   macros (js/Promise.resolve
                                           (do (sci/eval-form ctx (cond-> (list 'require (list 'quote macro-ns))
                                                                    reload (concat [:reload])))
                                               (let [publics (sci/eval-form ctx
                                                                            `(ns-publics '~macro-ns))
                                                     ks (keys publics)
                                                     vs (vals publics)
                                                     vs (map deref vs)
                                                     publics (zipmap ks vs)
                                                     publics (if refer
                                                               (select-keys publics refer)
                                                               publics)]
                                                 publics)))]
                               (.then macros
                                      (fn [macros]
                                        (set! compiler/built-in-macros
                                              ;; hack
                                              (merge compiler/built-in-macros macros))))))))
                  (js/Promise.resolve nil)
                  require-macros))))))

(defn compile-string [contents opts]
  (-> (js/Promise.resolve (scan-macros contents))
      (.then #(compiler/compile-string* contents opts))))

(defn compile-file [{:keys [in-file in-str out-file extension output-dir]
                     :or {output-dir ""}
                     :as opts}]
  (let [contents (or in-str (slurp in-file))]
    (-> (compile-string contents opts)
        (.then (fn [{:keys [javascript jsx] :as opts}]
                 (let [out-file (path/resolve output-dir
                                 (or out-file
                                     (str/replace in-file #".clj(s|c)$"
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
