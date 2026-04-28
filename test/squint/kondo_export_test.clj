(ns squint.kondo-export-test
  (:require
   [babashka.fs :as fs]
   [clj-kondo.core :as kondo]
   [clojure.test :refer [deftest is]]))

(def export-dir "resources/clj-kondo.exports/io.github.squint-cljs/squint")

(def fixture-source
  "(ns fixture)
(defn f [x] #?(:cljs (js-await x) :clj x))
(defn g [x] #?(:cljs (await x)    :clj x))
")

(deftest export-config-edn-exists
  (is (fs/exists? (fs/path export-dir "config.edn"))
      (str "kondo export must exist at " export-dir "/config.edn so downstream "
           "consumers pick it up via `clj-kondo --copy-configs --dependencies`")))

(deftest export-suppresses-js-await-and-await-warnings
  (let [tmp-dir (fs/create-temp-dir)
        fixture (fs/file tmp-dir "fixture.cljc")]
    (try
      (spit fixture fixture-source)
      (let [{:keys [findings]} (kondo/run! {:lint [(str fixture)]
                                            :config-dir export-dir})
            errors (filter #(= :error (:level %)) findings)]
        (is (empty? errors)
            (str "fixture using js-await and await should lint clean with "
                 "the export config; got: " (pr-str errors))))
      (finally
        (fs/delete-tree tmp-dir)))))
