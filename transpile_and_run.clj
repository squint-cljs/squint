(require '[babashka.process :refer [shell]]
         '[cherry.transpiler :as cherry]
         )

(when-not (System/getProperty "babashka.version")
  (require '[babashka.process.pprint]))

(def in-file (first *command-line-args*))
(def out-file (:out-file (cherry/transpile-file {:in-file in-file})))

(shell "node --experimental-fetch" out-file)
nil
