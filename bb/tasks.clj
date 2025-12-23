(ns tasks
  (:require
   [babashka.fs :as fs]
   [babashka.process :as p :refer [shell]]
   [cheshire.core :as json]
   [node-repl-tests]
   [clojure.string :as str]))

(def test-config
  '{:compiler-options {:load-tests true}
    :modules {:squint_tests {:init-fn squint.compiler-test/init
                             :depends-on #{:compiler :cljs.pprint :node}}}})

(defn shadow-extra-test-config []
  (merge-with
   merge
   test-config))

(defn bump-core-vars []
  (let [core-vars (:out (shell {:out :string}
                               "node --input-type=module -e 'import * as squint from \"squint-cljs/core.js\";console.log(JSON.stringify(Object.keys(squint)))'"))
        parsed (apply sorted-set (map symbol (json/parse-string core-vars)))]
    (spit "resources/squint/core.edn" (with-out-str
                                        ((requiring-resolve 'clojure.pprint/pprint)
                                         parsed)))))

(defn build-squint-npm-package []
  (fs/create-dirs ".work")
  (fs/delete-tree "lib")
  (fs/delete-tree ".shadow-cljs")
  (bump-core-vars)
  (spit ".work/config-merge.edn" "{}")
  (shell "npx shadow-cljs --config-merge .work/config-merge.edn release squint"))

(defn publish []
  (build-squint-npm-package)
  (run! fs/delete (fs/glob "lib" "*.map"))
  (shell "npx esbuild src/squint/core.js --minify --format=iife --global-name=squint.core --outfile=lib/squint.core.umd.js")
  (shell "npm publish"))

(defn watch-squint []
  (fs/create-dirs ".work")
  (fs/delete-tree ".shadow-cljs/builds/squint/dev/ana/squint")
  (spit ".work/config-merge.edn" (shadow-extra-test-config))
  (bump-core-vars)
  (shell "npx shadow-cljs --aliases :dev --config-merge .work/config-merge.edn watch squint"))

(defn test-project [_]
  (let [dir "test-project"]
    (fs/delete-tree (fs/path dir "lib"))
    ;; dummy invocation
    (shell {:dir dir} (fs/which "npx") "squint" "compile")
    (let [output (:out (shell {:dir dir :out :string} "node lib/main.mjs"))]
      (println output)
      (assert (str/includes? output "macros2/debug 10"))
      (assert (str/includes? output "macros2/debug 6"))
      (assert (str/includes? output "macros/debug 10"))
      (assert (str/includes? output "macros/debug 6"))
      (assert (str/includes? output "also 10"))
      (assert (str/includes? output "my-other-src 1 2"))
      (assert (str/includes? output "json!"))
      (assert (str/includes? output "{ a: 1 }"))
      (assert (str/includes? output "\"emit!\"")))
    (assert (fs/exists? "test-project/lib/foo.json"))
    (assert (fs/exists? "test-project/lib/baz.css"))
    (assert (not (fs/exists? "test-project/lib/bar.json")))))

(defn test-run [_]
  (let [dir "test-project"
        out (:out (shell {:dir dir :out :string} (fs/which "npx") "squint" "run" "script.cljs"))]
    (assert (str/includes? out "dude"))))

(defn test-squint []
  (fs/create-dirs ".work")
  (spit ".work/config-merge.edn" (shadow-extra-test-config))
  (bump-core-vars)
  (shell "npx shadow-cljs --config-merge .work/config-merge.edn compile squint")
  (shell "node lib/squint_tests.js")
  (node-repl-tests/run-tests {})
  (test-project {})
  (test-run {}))

(defn clojure-mode-test []
  (let [dir "libtests"]
    (fs/delete-tree dir)
    (fs/create-dir dir)
    (shell {:dir dir} "git clone https://github.com/nextjournal/clojure-mode")
    (let [dir (fs/path dir "clojure-mode")
          shell (partial p/shell {:dir dir})
          squint-local (fs/path dir "node_modules/squint-cljs")]
      (fs/create-dirs dir)
      (shell "yarn install")
      (fs/delete-tree squint-local)
      (fs/create-dirs squint-local)
      (run! #(fs/copy % squint-local) (fs/glob "." "*.{js,json}"))
      (prn :lib (fs/absolutize "lib") :squint-local (fs/absolutize (fs/path squint-local "lib")))
      (fs/copy-tree "lib" (fs/path squint-local "lib"))
      (fs/copy-tree "src" (fs/path squint-local "src"))
      (shell "node_modules/squint-cljs/node_cli.js" "compile")
      (shell "node dist/nextjournal/clojure_mode_tests.mjs")
      (println "clojure-mode tests successful!"))))

(defn eucalypt-test []
  (let [dir "libtests"]
    (fs/delete-tree dir)
    (fs/create-dir dir)
    (shell {:dir dir} "git clone https://github.com/chr15m/eucalypt")
    (let [dir (fs/path dir "eucalypt")
          shell (partial p/shell {:dir dir})
          squint-local (fs/path dir "node_modules/squint-cljs")]
      (fs/create-dirs dir)
      (shell "npm install")
      (fs/delete-tree squint-local)
      (fs/create-dirs squint-local)
      (run! #(fs/copy % squint-local) (fs/glob "." "*.{js,json}"))
      (fs/copy-tree "lib" (fs/path squint-local "lib"))
      (fs/copy-tree "src" (fs/path squint-local "src"))
      (shell "npm run test")
      (println "eucalypt tests successful!"))))

(defn libtests []
  #_(build-squint-npm-package)
  ;; temporarily disabled because of not= bug
  #_(eucalypt-test)
  (clojure-mode-test))
