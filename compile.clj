(require '[cherry.compiler :refer [transpile-string]])

(let [{:keys [imports exports body]}
      (transpile-string (slurp *in*))]
  (str imports exports body))
