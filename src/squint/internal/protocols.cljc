(ns squint.internal.protocols
  (:require [clojure.core :as core]
            [clojure.string :as str]))

(core/defn- emit-protocol-method-arity
  [method-sym args]
  `(~args
    ((unchecked-get ~(first args) ; this
                    ~method-sym) ~@args)))

(core/defn- emit-protocol-method
  [p method]
  (let [mname (first method)
        method-sym (symbol (str p "_" mname))
        [mdocs margs] (if (string? (second method))
                        [(second method) (drop 2 method)]
                        [nil (rest method)])
        this-sym (first margs)]
    `((def ~method-sym
        (js/Symbol ~(str p "_" mname)))
      (defn ~mname
        ~@(when mdocs [mdocs])
        ~@(map #(emit-protocol-method-arity method-sym %) margs)))))

(core/defn core-defprotocol
  [_&env _&form p & doc+methods]
  (core/let [[doc-and-opts methods] [(core/take-while #(not (list? %))
                                                      doc+methods)
                                     (core/drop-while #(not (list? %))
                                                      doc+methods)]
             pmeta (if (string? (first doc-and-opts))
                     (into {:doc (first doc-and-opts)}
                           (partition 2 (rest doc-and-opts)))
                     (into {} (partition 2 doc-and-opts)))]
    `(do
       (def ~(with-meta p pmeta) (js/Symbol ~(str p)))
       ~@(mapcat #(emit-protocol-method p %) methods))))

(core/defn ->impl-map [impls]
  (core/loop [ret {} s impls]
    (if (seq s)
      (recur (assoc ret (first s) (take-while seq? (next s)))
             (drop-while seq? (next s)))
      ret)))

;; https://github.com/clojure/clojurescript/blob/6aefc7354c3f7033d389634595d912f618c2abfc/src/main/clojure/cljs/core.cljc#L1303
(def ^:private js-type-sym->type
  '{object js/Object
    string js/String
    number js/Number
    array js/Array
    function js/Function
    boolean js/Boolean
    ;; TODO what to do here?
    default js/Object})

(core/defn- emit-type-method
  [psym type-sym method]
  (prn :psym psym :type-sym type-sym :method method)
  (let [mname (first method)
        msym (if (= 'Object psym)
               (str mname)
               (symbol (str psym "_" mname)))
        margs (second method)
        mbody (drop 2 method)
        this-arg (first margs)]
    `(let [f# (fn ~margs
                (~'js* "~{} = this; const self__ = this;" ~this-arg)
                ~@mbody)]
       (unchecked-set
        (.-prototype ~type-sym) ~msym f#))))

(core/defn- emit-type-methods
  [type-sym [psym pmethods]]
  `((unchecked-set
      (.-prototype ~type-sym)
      ~psym true)
     ~@(map #(emit-type-method psym type-sym %) pmethods)))

(core/defn core-extend-type
  [_&env _&form type-sym & impls]
  (core/let [type-sym (get js-type-sym->type type-sym type-sym)
             impl-map (->impl-map impls)]
    `(do
       ~@(mapcat #(emit-type-methods type-sym %) impl-map))))
