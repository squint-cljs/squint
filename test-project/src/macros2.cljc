(ns macros2)

(defmacro debug [_kwd body]
  [::debug body])
