(ns compile-time
  {:squint/compile-time true}
  (:require [clojure.string :as str]
            [ct-helper :as h]))

;; defmacro is auto-included in a flagged ns, no marker needed
(defmacro shout [x]
  `(str/join "-" [(str/upper-case ~x) "!!"]))

;; aliased emitted ref to a local project ns: h/... resolves to ct-helper
(defmacro formatted [x]
  `(h/format-it ~x))

;; bare same-ns ref: double-it resolves to compile-time/double-it
(defmacro doubled [x]
  `(double-it ~x))

;; compile-time helper: runs during expansion, uses clojure.string at compile time
^:squint/compile-time
(defn slugify [s]
  (str/replace (str/lower-case s) " " "-"))

;; macro calling a helper ns (clojure.string) at expansion; result baked into JS
(defmacro const-upper [s]
  (str/upper-case s))

;; macro calling the same-ns compile-time helper at expansion
(defmacro const-slug [s]
  (slugify s))

(defn double-it [x] (* 2 x))
