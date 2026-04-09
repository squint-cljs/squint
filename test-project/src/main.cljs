(ns main
  (:require-macros [macros :as m :refer [debug with-add-100]]
                   [macros-transitive :refer [wrapper with-format with-deep-format]]
                   [macros2 :refer [also]])
  (:require [macros :as m]
            [other-ns]
            [helpers]
            [my-other-src :as src]
            ["fs" :as fs]
            ["path" :as path]
            ["url" :as url :refer [fileURLToPath]]
            ["./test.json$default" :as json :with {:type :json}]))

(defn foo []
  (m/debug :foo (+ 1 2 3)))

(m/read-config)

(foo)
(debug :foo (also (+ 1 2 3 4)))

(src/debug :dude (+ 1 2 3))

(-> (js* "import.meta.url")
    fileURLToPath
    path/dirname
    (path/resolve "foo.json")
    (fs/readFileSync "UTF-8")
    js/JSON.parse
    .-a
    println)

(js/console.log json)

(println "qualified test:" (m/with-add-100 42))
(println "no-alias qualified:" (helpers/greet "world"))
(println "macro-dep:" (with-format "test" 999))
(wrapper :nested 444)
(println "deep-dep:" (with-deep-format "deep" 777))
