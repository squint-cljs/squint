(ns squint.internal.protocols
  (:require [clojure.core :as core]))

(core/defn- emit-protocol-method-arity
  [mname method-sym args]
  (let [this (first args)]
    `(~args
      (if (nil? ~this)
        ((unchecked-get ~mname nil)  ~@args)
        ((unchecked-get ~(first args) ;; this
                        ~method-sym) ~@args)))))

(core/defn- emit-protocol-method
  [p method]
  (let [mname (first method)
        method-sym (symbol (str p "_" mname))
        method (rest method)
        [mdocs margs] (if (string? (last method))
                        [(last method) (butlast method)]
                        [nil method])]
    `((def ~method-sym
        (js/Symbol ~(str p "_" mname)))
      (defn ~mname
        ~@(when mdocs [mdocs])
        ~@(map #(emit-protocol-method-arity mname method-sym %) margs)))))

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
       (def ~(with-meta p pmeta) {:__sym (js/Symbol ~(str p))})
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

(defn insert-this [method-bodies]
  (if (vector? (first method-bodies))
    (list* (first method-bodies)
           (with-meta (list 'js* "const self__ = this;")
             {:context :statement})
           (rest method-bodies))
    ;; multi-arity
    (map insert-this method-bodies)))

(core/defn- emit-type-method
  [psym type-sym method]
  (let [mname (first method)
        msym (if (= 'Object psym)
               (str mname)
               (symbol (str psym "_" mname)))
        f `(fn ~@(insert-this (rest method)))]
    (if (nil? type-sym)
      `(let [f# ~f]
         (unchecked-set
          ~mname
          ~type-sym f#))
      `(let [f# ~f]
         (unchecked-set
          (.-prototype ~type-sym) ~msym f#)))))

(core/defn- emit-type-methods
  [type-sym [psym pmethods]]
  (let [flag (if (nil? type-sym)
               `(unchecked-set
                 ~psym nil true)
               `(unchecked-set
                 (.-prototype ~type-sym)
                 (unchecked-get ~psym "__sym") true))]
    ;; (prn :flag flag)
    `(~flag
      ~@(map #(emit-type-method psym type-sym %) pmethods))))

(core/defn core-extend-type
  [_&env _&form type-sym & impls]
  (core/let [type-sym (get js-type-sym->type type-sym type-sym)
             impl-map (->impl-map impls)]
    `(do
       ~@(mapcat #(emit-type-methods type-sym %) impl-map))))

(core/defn- parse-impls [specs]
  (core/loop [ret {} s specs]
    (if (seq s)
      (recur (assoc ret (first s) (take-while seq? (next s)))
             (drop-while seq? (next s)))
      ret)))

(core/defn- emit-extend-protocol [p specs]
  (core/let [impls (parse-impls specs)]
    `(do
       ~@(map (core/fn [[t fs]]
                `(extend-type ~t ~p ~@fs))
              impls))))

(core/defn core-extend-protocol
  "Useful when you want to provide several implementations of the same
     protocol all at once. Takes a single protocol and the implementation
     of that protocol for one or more types. Expands into calls to
     extend-type:

     (extend-protocol Protocol
       AType
         (foo [x] ...)
         (bar [x y] ...)
       BType
         (foo [x] ...)
         (bar [x y] ...)
       AClass
         (foo [x] ...)
         (bar [x y] ...)
       nil
         (foo [x] ...)
         (bar [x y] ...))

     expands into:

     (do
      (clojure.core/extend-type AType Protocol
        (foo [x] ...)
        (bar [x y] ...))
      (clojure.core/extend-type BType Protocol
        (foo [x] ...)
        (bar [x y] ...))
      (clojure.core/extend-type AClass Protocol
        (foo [x] ...)
        (bar [x y] ...))
      (clojure.core/extend-type nil Protocol
        (foo [x] ...)
        (bar [x y] ...)))"
  [_ _ p & specs]
  (emit-extend-protocol p specs))
