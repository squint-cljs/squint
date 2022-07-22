(ns ns-form
  (:require
   ["fs" :as fs]
   ["process" :refer [version]]))

(js/console.log version)

(prn (fs/readFileSync "corpus/ns_form.cljs" "utf-8"))
