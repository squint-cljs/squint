(ns bump-versions
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def current-version (-> (slurp "package.json")
                         (json/parse-string true)
                         :version))

(def package-jsons (fs/glob "." "examples/**/package.json"))

(doseq [p package-jsons]
  (let [p (fs/file p)
        s (slurp p)
        re #"\"squint-cljs\": \"([^?]\d.*)\""]
    (when (re-find re s)
      (spit p (str/replace s re
                        (fn [[x v]]
                          (str/replace x v current-version)))))))
