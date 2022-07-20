(ns destructuring)

;; TODO:
(let [{:keys [a]} x]

  )

;; This currently transpiles as:

;; const {keys: [a]} = {a: 1};

;; What we probably want:

;; const _temp_object = x // the enti
;; const a = ...

