(ns macros)

(defmacro debug [_kwd body]
  [::debug body])
