(ns main
  (:require [macros :as m :refer [debug with-add-100]]
            [macros2 :refer [also with-add-200]]
            [macros-transitive :refer [wrapper format-it]]
            [compile-time :as ct :refer [shout doubled tripled]]
            [compile-time-cljs :as ctcljs]
            [other-ns]
            [macros-self :as ms]
            [my-other-src :as src]
            [greetlib.core :as greet]
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

(println "qualified test:" (with-add-100 42))
(println "refer-only qualified:" (with-add-200 42))
(wrapper 42)
(println "transitive-rt:" (format-it 42))
(println (greet/greeting "deps"))

(println "ct-shout:" (ct/shout "hi"))
(println "ct-shout-refer:" (shout "ho"))
(println "ct-formatted:" (ct/formatted 5))
(println "ct-doubled:" (doubled 21))
(println "ct-tripled:" (tripled 14))
(println "ct-const-upper:" (ct/const-upper "hi"))
(println "ct-const-slug:" (ct/const-slug "Hello World"))
(println "ct-cljs-yell:" (ctcljs/yell "hey"))
(println "ct-cljs-runtime:" (ctcljs/runtime-fn 41))
(println "self-require-macros:" ms/self-val (ms/twice-m 4))
