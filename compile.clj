(require '[cherry.compiler :refer [transpile-string]])

(println (transpile-string (slurp *in*)))

