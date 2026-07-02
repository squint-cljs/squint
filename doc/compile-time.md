# Compile-time namespaces

Macros run in squint's compiler (SCI on Node.js) such that the runtime (browser)
can remain minimal. Previously it was cumbersome to make an existing `.cljc`
namespace compatible with squint, if not all of the code of that namespace could
be evaluated through SCI. To solve this problem, a namespace can opt in to
loading only its squint compile-time parts using the special
`:squint/compile-time` annotation:

```clojure
(ns my.lib
  {:squint/compile-time true} ;; annotate this namespace for explicit compile time parts
  (:require [clojure.string :as str]))

;; a top-level defmacro is compile-time automatically
(defmacro shout [x]
  `(str/upper-case ~x))

;; mark any other compile-time form (an expansion helper, a defmethod)
^:squint/compile-time
(defn slugify [s]
  (str/replace (str/lower-case s) " " "-"))

(defmacro const-slug [s]
  (slugify s))

;; unmarked forms are runtime only and never evaluated in SCI
(defn runtime-thing [x]
  (inc x))
```

With the `:squint/compile-time` flag in the namespace's metadata, squint loads only the `defmacro`s and explicitly marked forms into SCI.
The rest of the file is compiled to JS as usual, with the
compile-time forms stripped.

Compile-time forms may live inside a `#?(:clj ...)` branch, the place a `.cljc`
namespace usually keeps them for JVM-hosted ClojureScript. Since the `:clj`
branch often contains JVM-only code (a macro may read JVM config at expansion
time), a form there needs the explicit marker:

```clojure
#?(:clj
   ^:squint/compile-time
   (defmacro shout [x]
     `(str/upper-case ~x)))
```

Unmarked `#?(:clj ...)` code (JVM interop, registrations, macros with JVM-only
bodies) is never evaluated by squint.
