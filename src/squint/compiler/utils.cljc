(ns squint.compiler.utils)

(defmulti emit (fn [expr _env] (type expr)))
