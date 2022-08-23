(ns clava.internal.deftype
  (:require [clava.internal.protocols :as p]
            [clojure.core :as core]))

(def fast-path-protocols
  "protocol fqn -> [partition number, bit]"
  (zipmap (map #(symbol "cljs.core" (core/str %))
               '[IFn ICounted IEmptyableCollection ICollection IIndexed ASeq ISeq INext
                 ILookup IAssociative IMap IMapEntry ISet IStack IVector IDeref
                 IDerefWithTimeout IMeta IWithMeta IReduce IKVReduce IEquiv IHash
                 ISeqable ISequential IList IRecord IReversible ISorted IPrintWithWriter IWriter
                 IPrintWithWriter IPending IWatchable IEditableCollection ITransientCollection
                 ITransientAssociative ITransientMap ITransientVector ITransientSet
                 IMultiFn IChunkedSeq IChunkedNext IComparable INamed ICloneable IAtom
                 IReset ISwap IIterable])
          (iterate (core/fn [[p b]]
                     (if (core/== 2147483648 b)
                       [(core/inc p) 1]
                       [p #?(:clj  (core/bit-shift-left b 1)
                             :cljs (core/* 2 b))]))
                   [0 1])))

(def fast-path-protocol-partitions-count
  "total number of partitions"
  (core/let [c (count fast-path-protocols)
             m (core/mod c 32)]
    (if (core/zero? m)
      (core/quot c 32)
      (core/inc (core/quot c 32)))))

(core/defn- prepare-protocol-masks [env impls]
  (core/let [resolve  identity #_(partial resolve-var env)
             impl-map (p/->impl-map impls)
             fpp-pbs  (seq
                       (keep fast-path-protocols
                             (map resolve
                                  (keys impl-map))))]
    (if fpp-pbs
      (core/let [fpps  (into #{}
                             (filter (partial contains? fast-path-protocols)
                                     (map resolve (keys impl-map))))
                 parts (core/as-> (group-by first fpp-pbs) parts
                         (into {}
                               (map (juxt key (comp (partial map peek) val))
                                    parts))
                         (into {}
                               (map (juxt key (comp (partial reduce core/bit-or) val))
                                    parts)))]
        [fpps (reduce (core/fn [ps p] (update-in ps [p] (core/fnil identity 0)))
                      parts
                      (range fast-path-protocol-partitions-count))]))))

(core/defn- collect-protocols [impls env]
  (core/->> impls
            (filter core/symbol?)
            (map identity #_#(:name (cljs.analyzer/resolve-var (dissoc env :locals) %)))
            (into #{})))

(core/defn- annotate-specs [annots v [f sigs]]
  (conj v
        (vary-meta (cons f (map #(cons (second %) (nnext %)) sigs))
                   merge annots)))

(core/defn dt->et
  ([type specs fields]
   (dt->et type specs fields false))
  ([type specs fields inline]
   (core/let [annots {:cljs.analyzer/type type
                      :cljs.analyzer/protocol-impl true
                      :cljs.analyzer/protocol-inline inline}]
     (core/loop [ret [] specs specs]
       (if (seq specs)
         (core/let [p     (first specs)
                    ret   (core/-> (conj ret p)
                                   (into (reduce (partial annotate-specs annots) []
                                                 (group-by first (take-while seq? (next specs))))))
                    specs (drop-while seq? (next specs))]
           (recur ret specs))
         ret)))))

(core/defn- build-positional-factory
  [rsym rname fields]
  (core/let [fn-name (with-meta (symbol (core/str '-> rsym))
                       (assoc (meta rsym) :factory :positional))
             docstring (core/str "Positional factory function for " rname ".")
             field-values (if (core/-> rsym meta :internal-ctor) (conj fields nil nil nil) fields)]
    `(defn ~fn-name
       ~docstring
       [~@fields]
       (new ~rname ~@field-values))))

(core/defn core-deftype
  "(deftype name [fields*]  options* specs*)
  Currently there are no options.
  Each spec consists of a protocol or interface name followed by zero
  or more method bodies:
  protocol-or-Object
  (methodName [args*] body)*
  The type will have the (by default, immutable) fields named by
  fields, which can have type hints. Protocols and methods
  are optional. The only methods that can be supplied are those
  declared in the protocols/interfaces.  Note that method bodies are
  not closures, the local environment includes only the named fields,
  and those fields can be accessed directly. Fields can be qualified
  with the metadata :mutable true at which point (set! afield aval) will be
  supported in method bodies. Note well that mutable fields are extremely
  difficult to use correctly, and are present only to facilitate the building
  of higherlevel constructs, such as ClojureScript's reference types, in
  ClojureScript itself. They are for experts only - if the semantics and
  implications of :mutable are not immediately apparent to you, you should not
  be using them.
  Method definitions take the form:
  (methodname [args*] body)
  The argument and return types can be hinted on the arg and
  methodname symbols. If not supplied, they will be inferred, so type
  hints should be reserved for disambiguation.
  Methods should be supplied for all methods of the desired
  protocol(s). You can also define overrides for methods of Object. Note that
  a parameter must be supplied to correspond to the target object
  ('this' in JavaScript parlance). Note also that recur calls to the method
  head should *not* pass the target object, it will be supplied
  automatically and can not be substituted.
  In the method bodies, the (unqualified) name can be used to name the
  class (for calls to new, instance? etc).
  One constructor will be defined, taking the designated fields.  Note
  that the field names __meta and __extmap are currently reserved and
  should not be used when defining your own types.
  Given (deftype TypeName ...), a factory function called ->TypeName
  will be defined, taking positional parameters for the fields"
  [&env _&form t fields & impls]
  #_(validate-fields "deftype" t fields)
  (core/let [env &env
             r t #_(:name (cljs.analyzer/resolve-var (dissoc env :locals) t))
             [fpps pmasks] (prepare-protocol-masks env impls)
             protocols (collect-protocols impls env)
             t (vary-meta t assoc
                          :protocols protocols
                          :skip-protocol-flag fpps)]
    `(do
       (deftype* ~t ~fields ~pmasks
         ~(when (seq impls)
            `(extend-type ~t ~@(dt->et t impls fields))))
       ~(build-positional-factory t r fields)
       ~t)))
