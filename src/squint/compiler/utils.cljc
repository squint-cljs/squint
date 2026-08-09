(ns squint.compiler.utils
  (:refer-clojure :exclude [munge]))

(defmulti emit (fn [expr _env] (type expr)))

(def js-reserved-words
  "Same list cljs.core/munge uses."
  #{"arguments" "abstract" "await" "boolean" "break" "byte" "case"
    "catch" "char" "class" "const" "continue"
    "debugger" "default" "delete" "do" "double"
    "else" "enum" "export" "extends" "final"
    "finally" "float" "for" "function" "goto" "if"
    "implements" "import" "in" "instanceof" "int"
    "interface" "let" "long" "native" "new"
    "package" "private" "protected" "public"
    "return" "short" "static" "super" "switch"
    "synchronized" "this" "throw" "throws"
    "transient" "try" "typeof" "var" "void"
    "volatile" "while" "with" "yield" "methods"
    "null" "constructor"})

(defn munge
  "Like clojure.core/munge, but appends $ to JavaScript reserved words, like
  cljs.core/munge does. clojure.core/munge does not, which made the JVM
  compiler emit invalid JS for e.g. (fn [this] ...)."
  [x]
  #?(:cljs (clojure.core/munge x)
     :clj (let [munged (clojure.core/munge x)]
            (if (contains? js-reserved-words (str munged))
              (if (symbol? munged)
                (symbol (str munged "$"))
                (str munged "$"))
              munged))))
