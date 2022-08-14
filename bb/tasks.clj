(ns tasks
  (:require
   [babashka.fs :as fs]
   [babashka.process :refer [shell]]
   [clojure.edn :as edn]
   [clojure.java.io :as io]))

(defn munge* [s reserved]
  (let [s (str (munge s))]
    (if (contains? reserved s)
      (str s "$")
      s)))

(defn shadow-extra-config
  []
  (let [core-config (edn/read-string (slurp (io/resource "clava/cljs.core.edn")))
        reserved (edn/read-string (slurp (io/resource "clava/js_reserved.edn")))
        vars (:vars core-config)
        ks (map #(symbol (munge* % reserved)) vars)
        vs (map #(symbol "cljs.core" (str %)) vars)
        core-map (zipmap ks vs)
        core-map (assoc core-map 'goog_typeOf 'goog/typeOf)]
    {:modules
     {:cljs_core {:exports core-map}}}))

(def test-config
  '{:compiler-options {:load-tests true}
    :modules {:clava_tests {:init-fn clava.compiler-test/init
                             :depends-on #{:compiler}}}})

(defn shadow-extra-test-config []
  (merge-with
   merge
   (shadow-extra-config)
   test-config))

(defn build-clava-npm-package []
  (fs/create-dirs ".work")
  (fs/delete-tree "lib")
  (fs/delete-tree ".shadow-cljs")
  (spit ".work/config-merge.edn" (shadow-extra-config))
  (shell "npx shadow-cljs --config-merge .work/config-merge.edn release clava"))

(defn publish []
  (build-clava-npm-package)
  (run! fs/delete (fs/glob "lib" "*.map"))
  (shell "npm publish"))

(defn watch-clava []
  (fs/create-dirs ".work")
  (spit ".work/config-merge.edn" (shadow-extra-test-config))
  (shell "npx shadow-cljs --config-merge .work/config-merge.edn watch clava"))

(defn test-clava []
  (fs/create-dirs ".work")
  (spit ".work/config-merge.edn" (shadow-extra-test-config))
  (shell "npx shadow-cljs --config-merge .work/config-merge.edn release clava")
  (shell "node lib/clava_tests.js"))
