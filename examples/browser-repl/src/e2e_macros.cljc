(ns e2e-macros)

;; Used by the browser-repl e2e test (issue #819): the test rewrites this macro
;; at runtime, touches the file that uses it, and asserts the persistent vite
;; compiler picks up the new definition instead of the first-loaded one.
(defmacro tag [] "macro-v1")
