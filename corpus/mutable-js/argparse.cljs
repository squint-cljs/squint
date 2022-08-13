(ns argparse
  (:require
   ["argparse" :as argparse :refer [ArgumentParser]]))

(def parser (ArgumentParser. {:prog "example.cljs"
                              :description "Example!"}))

(parser.add_argument "-f" "--foo" {:help "foo bar"})

(def args (js/process.argv.slice 4))

(prn (parser.parse_args args))

(let [x {:a {:b 2}}]
  (prn x.a.b))
