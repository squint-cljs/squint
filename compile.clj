(require '[cherry.compiler :refer [compile-string*]])

(let [{:keys [imports exports body]}
      (compile-string* (slurp *in*))]
  (str imports exports body))
