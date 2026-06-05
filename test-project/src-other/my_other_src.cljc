(ns my-other-src)

(let [x 1]
  (defn unused-externally-but-same-named-top-level-local [] x))

(let [x 2]
  (defn debug [_kwd _body]
    (println "my-other-src" (unused-externally-but-same-named-top-level-local) x)))
