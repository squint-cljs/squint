(ns tasks
  (:require
   [babashka.fs :as fs]
   [babashka.process :as p :refer [shell]]
   [cheshire.core :as json]
   [node-repl-tests]
   [node-cli-tests]
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
        ;; Drop names starting with "__" — convention for
        ;; exported-but-private helpers (e.g. __toFn) that other runtime
        ;; modules import but user CLJS shouldn't resolve as a core var.
        parsed (into (sorted-set)
                     (comp (remove #(str/starts-with? % "__"))
                           (map symbol))
                     (json/parse-string core-vars))]
    (spit "resources/squint/core.edn" (with-out-str
                                        ((requiring-resolve 'clojure.pprint/pprint)
                                         parsed)))))

(defn compile-test-runtime
  "Pre-compile the cljs.test runtime from its .cljs source so it ships in
  the npm package and is available to local tests. Skips the compile
  when test.js is already newer than its inputs — avoids reshuffling
  gensym-numbered locals on unchanged sources, which would otherwise
  produce a noisy diff on every build. Delete test.js (or touch a
  listed source) to force a rebuild."
  []
  (let [target  "src/squint/test.js"
        sources ["src/squint/test.cljs"
                 "src/squint/internal/test.cljc"]]
    (if (or (not (fs/exists? target))
            (seq (fs/modified-since target sources)))
      (shell "node" "node_cli.js" "compile" "--extension" "js" "src/squint/test.cljs")
      (println "[bb] test.js up to date — skipping recompile"))))

(defn build-squint-npm-package []
  (fs/create-dirs ".work")
  (fs/delete-tree "lib")
  (fs/delete-tree ".shadow-cljs")
  (bump-core-vars)
  (spit ".work/config-merge.edn" "{}")
  (shell "npx shadow-cljs --config-merge .work/config-merge.edn release squint")
  (compile-test-runtime))

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

(defn- compile-e2e []
  ;; compile the squint e2e harnesses (browser_repl_test, nrepl_node_test,
  ;; shared nrepl_client) once; the mjs is the same across vite versions
  (shell "node" "node_cli.js" "compile"
         "--paths" "e2e"
         "--output-dir" "e2e"
         "--extension" "mjs"))

(defn- nrepl-node-e2e []
  ;; nREPL server on the node path (local eval, no browser transport - how
  ;; `squint nrepl-server` runs): info/eldoc/complete incl. js/ completion
  (shell "node" "e2e/nrepl_node_test.mjs"))

(defn- browser-repl-sweep [vite]
  (doseq [v vite]
    (println "[browser-repl-test] installing" (str "vite@" v) "in the example")
    (shell {:dir "examples/browser-repl"} "npm" "install" (str "vite@" v))
    (shell "node" "e2e/browser_repl_test.mjs")))

(defn e2e
  "squint's own e2e tests (not example code). Lives in e2e/. One group:
  - node nREPL server path (no browser);
  - browser REPL: spawns `vite dev` against examples/browser-repl (isolated
    ports) + headless playwright browser + nREPL client.

  :vite - one or more vite majors (or full versions) to sweep for the browser
  test, each installed into the example before its run, so CI can cover all
  supported versions (`bb test:e2e --vite 5 --vite 6 --vite 7 --vite 8`).
  Default [\"8\"] (the newest we support). Always installed, so a run is
  deterministic regardless of what a prior run left in the example."
  {:org.babashka/cli {:coerce {:vite []}}}
  [{:keys [vite] :or {vite ["8"]}}]
  (compile-e2e)
  (nrepl-node-e2e)
  (browser-repl-sweep vite))

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
      (assert (str/includes? output "\"emit!\""))
      (assert (str/includes? output "qualified test: 142"))
      (assert (str/includes? output "refer-only qualified: 242"))
      (assert (str/includes? output "real-debug: 42"))
      (assert (str/includes? output "transitive-rt: debug: 42"))
      ;; :deps with :local/root resolves to a source dir added to :paths
      (assert (str/includes? output "greetlib hello deps")))
    (assert (fs/exists? "test-project/lib/greetlib/core.mjs"))
    (assert (fs/exists? "test-project/lib/foo.json"))
    (assert (fs/exists? "test-project/lib/baz.css"))
    (assert (not (fs/exists? "test-project/lib/bar.json")))))

(defn test-run [_]
  (let [dir "test-project"
        out (:out (shell {:dir dir :out :string} (fs/which "npx") "squint" "run" "script.cljs"))]
    (assert (str/includes? out "dude"))))

(defn test-cljs-test [_]
  (let [src "test-resources/cljs_test_smoke.cljs"
        out-file "test-resources/cljs_test_smoke.mjs"]
    (shell "node" "node_cli.js" "compile" src)
    (let [out (:out (shell {:out :string} "node" out-file))]
      (fs/delete out-file)
      (assert (str/includes? out "Ran 11 tests containing 21 assertions") out)
      (assert (str/includes? out "1 failures, 0 errors") out)
      ;; :begin-test-ns must fire for each ns visited by run-tests
      (assert (str/includes? out "Testing ns.a") out)
      (assert (str/includes? out "Testing ns.b") out)
      ;; (run-tests 'synthetic.ns) macro must compile to a string lookup
      (assert (str/includes? out "Testing synthetic.ns") out))))

(defn test-squint []
  (fs/create-dirs ".work")
  (spit ".work/config-merge.edn" (shadow-extra-test-config))
  (bump-core-vars)
  (shell "npx shadow-cljs --config-merge .work/config-merge.edn compile squint")
  (compile-test-runtime)
  (shell "node --expose-gc lib/squint_tests.js")
  (node-repl-tests/run-tests {})
  (node-cli-tests/run-tests {})
  (test-project {})
  (test-run {})
  (test-cljs-test {}))

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

(defn replicant-test []
  (let [dir "libtests"]
    (fs/delete-tree dir)
    (fs/create-dir dir)
    (shell {:dir dir} "git clone https://github.com/cjohansen/replicant")
    (let [dir (fs/path dir "replicant")
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
      (println "replicant tests successful!"))))

(defn babashka-cli-test []
  (let [dir "libtests"]
    (fs/delete-tree dir)
    (fs/create-dir dir)
    (shell {:dir dir} "git clone https://github.com/babashka/cli")
    (let [dir (fs/path dir "cli")
          shell (partial p/shell {:dir dir})
          squint-local (fs/path dir "node_modules/squint-cljs")]
      (fs/create-dirs dir)
      (shell "npm install")
      (fs/delete-tree squint-local)
      (fs/create-dirs squint-local)
      (run! #(fs/copy % squint-local) (fs/glob "." "*.{js,json}"))
      (fs/copy-tree "lib" (fs/path squint-local "lib"))
      (fs/copy-tree "src" (fs/path squint-local "src"))
      (shell "node_modules/.bin/squint" "compile")
      (shell "node" ".squint-out/babashka/run_tests.mjs")
      (println "babashka.cli tests successful!"))))

(defn libtests []
  #_(build-squint-npm-package)
  ;; temporarily disabled because of not= bug
  #_(eucalypt-test)
  (clojure-mode-test)
  (replicant-test)
  (babashka-cli-test))
