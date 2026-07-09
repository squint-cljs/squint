(ns build
  (:require
   [clojure.string :as str]
   [clojure.tools.build.api :as b]))

(def lib 'io.github.squint-cljs/squint)
(def version
  (second (re-find #"\"version\":\s*\"([^\"]+)\"" (slurp "package.json"))))
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def basis (b/create-basis {:project "deps.edn"}))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (clean nil)
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src"]
                :scm {:url "https://github.com/squint-cljs/squint"
                      :connection "scm:git:git@github.com:squint-cljs/squint.git"
                      :developerConnection "scm:git:git@github.com:squint-cljs/squint.git"
                      :tag (format "v%s" version)}
                :pom-data
                [[:description "Light-weight ClojureScript dialect"]
                 [:url "https://github.com/squint-cljs/squint"]
                 [:licenses
                  [:license
                   [:name "Eclipse Public License 1.0"]
                   [:url "https://www.eclipse.org/legal/epl-v10.html"]]]]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn deploy [opts]
  (jar opts)
  (try ((requiring-resolve 'deps-deploy.deps-deploy/deploy)
        (merge {:installer :remote
                :artifact jar-file
                :pom-file (b/pom-path {:lib lib :class-dir class-dir})}
               opts))
       (catch Exception e
         (if-not (str/includes? (ex-message e) "redeploying non-snapshots is not allowed")
           (throw e)
           (println "This release was already deployed."))))
  opts)
