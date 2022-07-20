(ns cherry-test
  (:require
   [cljs.core.server]
   [cljs.repl.node]
   [clojure.java.shell :refer [sh]]
   [clojure.test :as t :refer [deftest is]]
   [com.reasonr.scriptjure :refer [js]]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [babashka.fs :as fs]))

(defn js-eval [expr]
  (fs/create-dirs "target")
  (spit "target/out.mjs" expr)
  (let [res (sh "node" "--experimental-fetch" "test/out.mjs")]
    (if (not (zero? (:exit res)))
      (throw (Exception. (:err res)))
      (:out res))))

(deftest cherry-test
  (let [out (js-eval (js
                      (var mori (.-default (await (import "mori"))))
                      ;; (console.log mori)
                      (console.log (mori.toJs (mori.map (fn [i]
                                                          (console.log i)
                                                          i) [1 2 3])))
                      (defn ^:async foo [url]
                        (console.log "Fetching:" url)
                        (let [resp (await (fetch url))
                              status (await (.-status resp))]
                          status))

                      (defn non_async []
                        (js/Promise.resolve 1))

                      (console.log (+ 1 (await (foo "https://clojure.org"))))
                      (await (.then (non_async) (fn [n]
                                                  (console.log n))))
                      (console.log "success")))]
    (is (str/includes? out "success"))))
