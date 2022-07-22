;;; string.clj -- functional string utilities for Clojure

;; by Stuart Sierra, http://stuartsierra.com/
;; January 26, 2010

;; Copyright (c) Stuart Sierra, 2010. All rights reserved.  The use
;; and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this
;; distribution.  By using this software in any fashion, you are
;; agreeing to be bound by the terms of this license.  You must not
;; remove this notice, or any other, from this software.


;; This file mostly contains methods that disappeared between clojure 1.2 and 1.3

(ns com.reasonr.string
  (:refer-clojure :exclude (tail drop get)))

(defn ^String tail
  "Returns the last n characters of s."
  [n ^String s]
  (if (< (count s) n)
    s
    (.substring s (- (count s) n))))

(defn ^String drop
  "Drops first n characters from s.  Returns an empty string if n is
  greater than the length of s."
  [n ^String s]
  (if (< (count s) n)
    ""
    (.substring s n)))

(defn ^String get
  "Gets the i'th character in string."
  {:deprecated "1.2"}
  [^String s i]
  (.charAt s i))