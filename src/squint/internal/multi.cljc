(ns squint.internal.multi
  (:refer-clojure :exclude [defmulti defmethod remove-method
                            prefer-method prefers methods get-method
                            remove-all-methods
                            isa? derive underive make-hierarchy
                            parents ancestors descendants])
  (:require [clojure.core :as core]
            [clojure.string :as str]))

(core/defn- flag! [env]
  (when-let [atm (:need-multi-import env)]
    (reset! atm true)))

(core/defn- multi-call [js-name args]
  (let [tmpl (str "squint_multi." js-name "("
                  (str/join ", " (repeat (count args) "~{}"))
                  ")")]
    `(~'js* ~tmpl ~@args)))

(core/defn core-defmulti
  "(defmulti name dispatch-fn) or
   (defmulti name docstring? attr-map? dispatch-fn & options)"
  [_&form env mm-name & args]
  (flag! env)
  (let [[args docstring] (if (string? (first args))
                           [(next args) (first args)]
                           [args nil])
        [args attr-map] (if (map? (first args))
                          [(next args) (first args)]
                          [args nil])
        dispatch-fn (first args)
        opts (apply hash-map (next args))
        opts-js (cond-> {}
                  (contains? opts :default) (assoc "default" (:default opts))
                  (contains? opts :hierarchy) (assoc "hierarchy" (:hierarchy opts)))
        m (merge (if docstring {:doc docstring} {}) attr-map)
        mm-name* (if (seq m) (with-meta mm-name m) mm-name)]
    `(def ~mm-name* ~(multi-call "defmulti" [(str mm-name) dispatch-fn opts-js]))))

(core/defn core-defmethod
  "(defmethod multifn dispatch-val [args*] body)"
  [_&form env multifn dispatch-val & fn-tail]
  (flag! env)
  (multi-call "defmethod" [multifn dispatch-val `(fn ~@fn-tail)]))

(core/defn core-get-method [_&form env mf dv]
  (flag! env)
  (multi-call "get_method" [mf dv]))

(core/defn core-methods [_&form env mf]
  (flag! env)
  (multi-call "methods" [mf]))

(core/defn core-remove-method [_&form env mf dv]
  (flag! env)
  (multi-call "remove_method" [mf dv]))

(core/defn core-remove-all-methods [_&form env mf]
  (flag! env)
  (multi-call "remove_all_methods" [mf]))

(core/defn core-prefer-method [_&form env mf a b]
  (flag! env)
  (multi-call "prefer_method" [mf a b]))

(core/defn core-prefers [_&form env mf]
  (flag! env)
  (multi-call "prefers" [mf]))

(core/defn core-isa? [_&form env & args]
  (flag! env)
  (multi-call "isa_QMARK_" args))

(core/defn core-derive [_&form env & args]
  (flag! env)
  (multi-call "derive" args))

(core/defn core-underive [_&form env & args]
  (flag! env)
  (multi-call "underive" args))

(core/defn core-make-hierarchy [_&form env]
  (flag! env)
  (multi-call "make_hierarchy" []))

(core/defn core-parents [_&form env & args]
  (flag! env)
  (multi-call "parents" args))

(core/defn core-ancestors [_&form env & args]
  (flag! env)
  (multi-call "ancestors" args))

(core/defn core-descendants [_&form env & args]
  (flag! env)
  (multi-call "descendants" args))
