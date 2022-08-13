(ns argparse
  (:require
   ["argparse" :as argparse :refer [ArgumentParser]]))

(def parser (ArgumentParser. {:prog "example.cljs"
                              :description "Example!"}))

(parser.add_argument "-f" "--foo" {:help "foo bar yolo"})

(def args (js/process.argv.slice 4))

(js/console.log (parser.parse_args args))

