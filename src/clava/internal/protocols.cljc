(ns clava.internal.protocols
  (:require [clojure.core :as core]))

(core/defn core-defprotocol
  [&env _&form p & doc+methods]
  (core/let [[doc-and-opts methods] [(core/take-while #(not (list? %))
                                                      doc+methods)
                                     (core/drop-while #(not (list? %))
                                                      doc+methods)]
             ns-name (core/-> &env :ns :name)]
    `(do
       (def ~p (js/Symbol ~(str ns-name "/" p)))
       ~@(for [method methods
               :let [mname (first method)
                     method-sym (symbol (str p "_" mname))
                     margs (second method)
                     this-sym (first margs)

                     #_#_mdocs (nth method 2)]]
           `(do
              (def ~method-sym
                (js/Symbol ~(str ns-name "/" mname)))
              (defn ~mname
                ~margs
                ((unchecked-get ~this-sym ~method-sym) ~@margs)))))))


(comment
  (core-defprotocol
   {:ns {:name "foo.bar"}}
   nil
   'Transformer
   "asdf"
   :extend-via-metadata true
   '(init [tf])
   '(step [tf result el])
   '(result [tf result])))

(core/defn ->impl-map [impls]
  (core/loop [ret {} s impls]
    (if (seq s)
      (recur (assoc ret (first s) (take-while seq? (next s)))
             (drop-while seq? (next s)))
      ret)))

(def ^:private js-type-sym->type
  '{object js/Object
    string js/String
    number js/Number
    array js/Array
    function js/Function
    boolean js/Boolean
    ;; TODO what to do here?
    default js/Object})

(core/defn- emit-method
  [psym type-sym method]
  (let [mname (first method)
        msym (symbol (str psym "_" mname))
        margs (second method)
        mbody (drop 2 method)]
    `(unchecked-set
      (.-prototype ~type-sym) ~msym
      (fn ~margs ~@mbody))))

(core/defn- emit-methods
  [type-sym [psym pmethods]]
  `((unchecked-set
      (.-prototype ~type-sym)
      ~psym true)
     ~@(map #(emit-method psym type-sym %) pmethods)))

(core/defn core-extend-type
  [&env _&form type-sym & impls]
  (core/let [type-sym (get js-type-sym->type type-sym type-sym)
             impl-map (->impl-map impls)]
    `(do
       ~@(mapcat #(emit-methods type-sym %) impl-map))))

(comment
  (core-extend-type
   {}
   nil
   'Mapping
   't/Transformer
   '(init [_] (rf))
   '(step [_ result el]
          (rf result (f el)))
   '(result [_ result]
            (rf result))))
