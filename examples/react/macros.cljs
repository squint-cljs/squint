(ns react.macros)

(defmacro $
  "Render element (keyword or symbol) with optional props"
  ([elt]
   `($ ~elt nil))
  ([elt props & children]
   (let [elt (if (keyword? elt) (name elt) elt)]
     (if (map? props)
       `(react/createElement ~elt (clj->js ~props) ~@children)
       `(react/createElement ~elt nil ~props ~@children)))))
