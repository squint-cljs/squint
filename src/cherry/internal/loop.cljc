(ns cherry.internal.loop
  (:require [cherry.internal.destructure :refer [destructure]]))

(defn core-loop
  "Evaluates the exprs in a lexical context in which the symbols in
  the binding-forms are bound to their respective init-exprs or parts
  therein. Acts as a recur target."
  [_&form _&bindings bindings & body]
  #_(assert-args loop
    (vector? bindings) "a vector for its binding"
    (even? (count bindings)) "an even number of forms in binding vector")
  (let [db (destructure bindings)]
    (if (= db bindings)
      `(loop* ~bindings ~@body)
      (let [vs (take-nth 2 (drop 1 bindings))
                 bs (take-nth 2 bindings)
                 gs (map (fn [b] (if (symbol? b) b (gensym))) bs)
                 bfs (reduce (fn [ret [b v g]]
                               (if (symbol? b)
                                 (conj ret g v)
                                 (conj ret g v b g)))
                       [] (map vector bs vs gs))]
        `(let ~bfs
           (loop* ~(vec (interleave gs gs))
             (let ~(vec (interleave bs gs))
               ~@body)))))))
