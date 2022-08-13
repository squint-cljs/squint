(ns argparse
  (:require
   ["argparse" :as argparse :refer [ArgumentParser]]))

(def parser (ArgumentParser. {:prog "example.cljs"
                              :description "Example!"}))

(parser.add_argument "-f" "--foo" {:help "foo bar"})

(def args (.slice js/process.argv 4))

(js/console.dir (.parse_args parser args))
