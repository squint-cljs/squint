(ns cherry.internal.protocols
  (:require [clojure.core :as core]))

(core/defn- protocol-prefix [psym]
  (core/str (core/-> (core/str psym)
                     (.replace #?(:clj \. :cljs (js/RegExp. "\\." "g")) \$)
                     (.replace \/ \$))
            "$"))

(defn core-unchecked-get [obj key]
  (list 'js* "(~{}[~{}])" obj key))

(core/defmacro core-defprotocol
  "A protocol is a named set of named methods and their signatures:
  (defprotocol AProtocolName
    ;optional doc string
    \"A doc string for AProtocol abstraction\"
  ;method signatures
    (bar [this a b] \"bar docs\")
    (baz [this a] [this a b] [this a b c] \"baz docs\"))
  No implementations are provided. Docs can be specified for the
  protocol overall and for each method. The above yields a set of
  polymorphic functions and a protocol object. All are
  namespace-qualified by the ns enclosing the definition The resulting
  functions dispatch on the type of their first argument, which is
  required and corresponds to the implicit target object ('this' in
  JavaScript parlance). defprotocol is dynamic, has no special compile-time
  effect, and defines no new types.
  (defprotocol P
    (foo [this])
    (bar-me [this] [this y]))
  (deftype Foo [a b c]
    P
    (foo [this] a)
    (bar-me [this] b)
    (bar-me [this y] (+ c y)))
  (bar-me (Foo. 1 2 3) 42)
  => 45
  (foo
    (let [x 42]
      (reify P
        (foo [this] 17)
        (bar-me [this] x)
        (bar-me [this y] x))))
  => 17"
  [psym & doc+methods]
  (core/let [p psym #_(:name (cljs.analyzer/resolve-var (dissoc &env :locals) psym))
             [opts methods]
             (core/loop [opts {:protocol-symbol true}
                         methods []
                         sigs doc+methods]
               (core/if-not (seq sigs)
                 [opts methods]
                 (core/let [[head & tail] sigs]
                   (core/cond
                     (core/string? head)
                     (recur (assoc opts :doc head) methods tail)
                     (core/keyword? head)
                     (recur (assoc opts head (first tail)) methods (rest tail))
                     (core/seq? head)
                     (recur opts (conj methods head) tail)
                     :else
                     (throw #?(:clj  (Exception.
                                       (core/str "Invalid protocol, " psym " received unexpected argument"))
                               :cljs (js/Error.
                                       (core/str "Invalid protocol, " psym " received unexpected argument"))))
                     ))))
             psym (vary-meta psym merge opts)
             ns-name (core/-> &env :ns :name)
             ;; TODO
             fqn (core/fn [n] (symbol nil #_(core/str ns-name) (core/str n)))
             prefix (protocol-prefix p)
             _ (core/doseq [[mname & arities] methods]
                 (core/when (some #{0} (map count (filter vector? arities)))
                   (throw
                     #?(:clj (Exception.
                               (core/str "Invalid protocol, " psym
                                 " defines method " mname " with arity 0"))
                        :cljs (js/Error.
                                (core/str "Invalid protocol, " psym
                                  " defines method " mname " with arity 0"))))))
             sig->syms (core/fn [sig]
                         (core/if-not (every? core/symbol? sig)
                           (mapv (core/fn [arg]
                                   (core/cond
                                     (core/symbol? arg) arg
                                     (core/and (map? arg) (core/some? (:as arg))) (:as arg)
                                     :else (gensym))) sig)
                           sig))
             expand-dyn (core/fn [fname sig]
                          (core/let [sig (sig->syms sig)

                                     fqn-fname (with-meta (fqn fname) {:cljs.analyzer/no-resolve true})
                                     fsig (first sig)

                                     ;; construct protocol checks in reverse order
                                     ;; check the.protocol/fn["_"] for default impl last
                                     check
                                     `(let [m# (unchecked-get ~fqn-fname "_")]
                                        (if-not (nil? m#)
                                          (m# ~@sig)
                                          (throw
                                            (missing-protocol
                                              ~(core/str psym "." fname) ~fsig))))

                                     ;; then check protocol on js string,function,array,object (first dynamic check actually executed)
                                     check
                                     `(let [x# (if (nil? ~fsig) nil ~fsig)
                                            m# (unchecked-get ~fqn-fname (cljs.core/goog_typeOf x#))]
                                        (if-not (nil? m#)
                                          (m# ~@sig)
                                          ~check))]
                            `(~sig ~check)))
             expand-sig (core/fn [fname dyn-name slot sig]
                          (core/let [sig (sig->syms sig)

                                     fqn-fname (with-meta (fqn fname) {:cljs.analyzer/no-resolve true})
                                     fsig (first sig)

                                     ;; check protocol property on object (first check executed)
                                     check
                                     `(if (and (not (nil? ~fsig))
                                               ;; Property access needed here.
                                               (not (nil? (. ~fsig ~(with-meta (symbol (core/str "-" slot)) {:protocol-prop true})))))
                                        (. ~fsig ~slot ~@sig)
                                        (~dyn-name ~@sig))

                                     ;; then check protocol fn in metadata (only when protocol is marked with :extend-via-metadata true)
                                     check
                                     (core/if-not (:extend-via-metadata opts)
                                       check
                                       `(if-let [meta-impl# (-> ~fsig (core/meta) (core/get '~fqn-fname))]
                                          (meta-impl# ~@sig)
                                          ~check))]
                            `(~sig ~check)))
             psym (core/-> psym
                    (vary-meta update-in [:jsdoc] conj "@interface")
                    (vary-meta assoc-in [:protocol-info :methods]
                      (into {}
                        (map
                          (core/fn [[fname & sigs]]
                            (core/let [doc (core/as-> (last sigs) doc
                                             (core/when (core/string? doc) doc))
                                       sigs (take-while vector? sigs)]
                              [(vary-meta fname assoc :doc doc)
                               (vec sigs)]))
                          methods)))
                    ;; for compatibility with Clojure
                    (vary-meta assoc-in [:sigs]
                      (into {}
                        (map
                          (core/fn [[fname & sigs]]
                            (core/let [doc (core/as-> (last sigs) doc
                                             (core/when (core/string? doc) doc))
                                       sigs (take-while vector? sigs)]
                              [(keyword fname) {:name fname :arglists (list* sigs) :doc doc}]))
                          methods))))
             method (core/fn [[fname & sigs]]
                      (core/let [doc (core/as-> (last sigs) doc
                                       (core/when (core/string? doc) doc))
                                 sigs (take-while vector? sigs)
                                 #_#_amp (core/when (some #{'&} (apply concat sigs))
                                       (cljs.analyzer/warning
                                        :protocol-with-variadic-method
                                        &env {:protocol psym :name fname}))
                                 _ (core/when-some [existing (core/get (-> &env :ns :defs) fname)]
                                     #_(core/when-not (= p (:protocol existing))
                                       (cljs.analyzer/warning
                                         :protocol-with-overwriting-method
                                         {} {:protocol psym :name fname :existing existing})))
                                 slot (symbol (core/str prefix (munge (name fname))))
                                 dyn-name (symbol (core/str slot "$dyn"))
                                 fname (vary-meta fname assoc
                                         :protocol p
                                         :doc doc)]
                        `(let [~dyn-name (core/fn
                                           ~@(map (core/fn [sig]
                                                    (expand-dyn fname sig))
                                               sigs))]
                           (defn ~fname
                             ~@(map (core/fn [sig]
                                      (expand-sig fname dyn-name
                                        (with-meta (symbol (core/str slot "$arity$" (count sig)))
                                          {:protocol-prop true})
                                        sig))
                                    sigs)))))
             ret `(do
                    #_(set! ~'*unchecked-if* true)
                    (def ~psym (~'js* "function(){}"))
                    ~@(map method methods)
                    #_(set! ~'*unchecked-if* false))]
    ret))

(core/defn ->impl-map [impls]
  (core/loop [ret {} s impls]
    (if (seq s)
      (recur (assoc ret (first s) (take-while seq? (next s)))
             (drop-while seq? (next s)))
      ret)))

(core/defn- type-hint-first-arg
  [type-sym argv]
  (assoc argv 0 (vary-meta (argv 0) assoc :tag type-sym)))

(core/defn- type-hint-single-arity-sig
  [type-sym sig]
  (list* (first sig) (type-hint-first-arg type-sym (second sig)) (nnext sig)))

(core/defn- type-hint-multi-arity-sig
  [type-sym sig]
  (list* (type-hint-first-arg type-sym (first sig)) (next sig)))

(core/defn- type-hint-multi-arity-sigs
  [type-sym sigs]
  (list* (first sigs) (map (partial type-hint-multi-arity-sig type-sym) (rest sigs))))

(core/defn- type-hint-sigs
  [type-sym sig]
  (if (vector? (second sig))
    (type-hint-single-arity-sig type-sym sig)
    (type-hint-multi-arity-sigs type-sym sig)))

(core/defn- type-hint-impl-map
  [type-sym impl-map]
  (reduce-kv (core/fn [m proto sigs]
               (assoc m proto (map (partial type-hint-sigs type-sym) sigs)))
             {} impl-map))

(def ^:private base-type
  {nil "null"
   'object "object"
   'string "string"
   'number "number"
   'array "array"
   'function "function"
   'boolean "boolean"
   'default "_"})

(def ^:private js-base-type
  {'js/Boolean "boolean"
   'js/String "string"
   'js/Array "array"
   'js/Object "object"
   'js/Number "number"
   'js/Function "function"})

(core/defn- base-assign-impls [env resolve tsym type [p sigs]]
  #_(update-protocol-var p tsym env)
  (core/let [psym       (resolve p)
             pfn-prefix (subs (core/str psym) 0
                              (clojure.core/inc (.indexOf (core/str psym) "/")))]
    (cons `(unchecked-set ~psym ~type true)
          (map (core/fn [[f & meths :as form]]
                 `(unchecked-set ~(symbol (core/str pfn-prefix f))
                                 ~type ~(with-meta `(fn ~@meths) (meta form))))
               sigs))))

(core/defmulti ^:private extend-prefix (core/fn [tsym sym] (core/-> tsym meta :extend)))


(core/defn- to-property [sym]
  (symbol (core/str "-" sym)))

(core/defmethod extend-prefix :instance
  [tsym sym] `(.. ~tsym ~(to-property sym)))

(core/defmethod extend-prefix :default
  [tsym sym]
  (with-meta `(.. ~tsym ~'-prototype ~(to-property sym)) {:extend-type true}))


(core/defn- adapt-obj-params [type [[this & args :as sig] & body]]
  (core/list (vec args)
             (list* 'this-as (vary-meta this assoc :tag type) body)))

(core/defn- add-obj-methods [type type-sym sigs]
  (map (core/fn [[f & meths :as form]]
         (core/let [[f meths] (if (vector? (first meths))
                                [f [(rest form)]]
                                [f meths])]
           `(set! ~(extend-prefix type-sym f)
                  ~(with-meta `(fn ~@(map #(adapt-obj-params type %) meths)) (meta form)))))
       sigs))

(core/defn- adapt-ifn-invoke-params [type [[this & args :as sig] & body]]
  `(~(vec args)
     (this-as ~(vary-meta this assoc :tag type)
       ~@body)))

(core/defn- adapt-ifn-params [type [[this & args :as sig] & body]]
  (core/let [self-sym (with-meta 'self__ {:tag type})]
    `(~(vec (cons self-sym args))
      (this-as ~self-sym
        (let [~this ~self-sym]
          ~@body)))))

(core/defn- adapt-proto-params [type [[this & args :as sig] & body]]
  (core/let [this' (vary-meta this assoc :tag type)]
    `(~(vec (cons this' args))
      (this-as ~this'
        ~@body))))

(core/defn- ifn-invoke-methods [type type-sym [f & meths :as form]]
  (map
    (core/fn [meth]
      (core/let [arity (count (first meth))]
        `(set! ~(extend-prefix type-sym (symbol (core/str "cljs$core$IFn$_invoke$arity$" arity)))
           ~(with-meta `(fn ~meth) (meta form)))))
    (map #(adapt-ifn-invoke-params type %) meths)))

(core/defn- add-ifn-methods [type type-sym [f & meths :as form]]
  (core/let [meths    (map #(adapt-ifn-params type %) meths)
             this-sym (with-meta 'self__ {:tag type})
             argsym   (gensym "args")
             max-ifn-arity 20]
    (concat
     [`(set! ~(extend-prefix type-sym 'call) ~(with-meta `(fn ~@meths) (meta form)))
      `(set! ~(extend-prefix type-sym 'apply)
             ~(with-meta
                `(fn ~[this-sym argsym]
                   (this-as ~this-sym
                     (let [args# (cljs.core/aclone ~argsym)]
                       (.apply (.-call ~this-sym) ~this-sym
                               (.concat (array ~this-sym)
                                        (if (> (.-length args#) ~max-ifn-arity)
                                          (doto (.slice args# 0 ~max-ifn-arity)
                                            (.push (.slice args# ~max-ifn-arity)))
                                          args#))))))
                (meta form)))]
     (ifn-invoke-methods type type-sym form))))


(core/defn- add-proto-methods* [pprefix type type-sym [f & meths :as form]]
  (core/let [pf (core/str pprefix (munge (name f)))]
    (if (vector? (first meths))
      ;; single method case
      (core/let [meth meths]
        [`(set! ~(extend-prefix type-sym (core/str pf "$arity$" (count (first meth))))
                ~(with-meta `(fn ~@(adapt-proto-params type meth)) (meta form)))])
      (map (core/fn [[sig & body :as meth]]
             `(set! ~(extend-prefix type-sym (core/str pf "$arity$" (count sig)))
                    ~(with-meta `(fn ~(adapt-proto-params type meth)) (meta form))))
           meths))))

(core/defn- proto-assign-impls [env resolve type-sym type [p sigs]]
  #_(update-protocol-var p type env)
  (core/let [psym      (resolve p)
             pprefix   (protocol-prefix psym)
             skip-flag (set (core/-> type-sym meta :skip-protocol-flag))]
    (if (= p 'Object)
      (add-obj-methods type type-sym sigs)
      (concat
       (core/when-not (skip-flag psym)
         [`(set! ~(extend-prefix type-sym pprefix) cljs.core/PROTOCOL_SENTINEL)])
       (mapcat
        (core/fn [sig]
          (if (= psym 'cljs.core/IFn)
            (add-ifn-methods type type-sym sig)
            (add-proto-methods* pprefix type type-sym sig)))
        sigs)))))

(core/defmacro core-extend-type
  "Extend a type to a series of protocols. Useful when you are
  supplying the definitions explicitly inline. Propagates the
  type as a type hint on the first argument of all fns.
  type-sym may be
   * default, meaning the definitions will apply for any value,
     unless an extend-type exists for one of the more specific
     cases below.
   * nil, meaning the definitions will apply for the nil value.
   * any of object, boolean, number, string, array, or function,
     indicating the definitions will apply for values of the
     associated base JavaScript types. Note that, for example,
     string should be used instead of js/String.
   * a JavaScript type not covered by the previous list, such
     as js/RegExp.
   * a type defined by deftype or defrecord.
  (extend-type MyType
    ICounted
    (-count [c] ...)
    Foo
    (bar [x y] ...)
    (baz ([x] ...) ([x y] ...) ...)"
  [type-sym & impls]
  (core/let [env &env
             ;; _ (validate-impls env impls)
             resolve identity #_(partial resolve-var env)
             impl-map (->impl-map impls)
             impl-map (if ('#{boolean number} type-sym)
                        (type-hint-impl-map type-sym impl-map)
                        impl-map)
             [type assign-impls] (core/if-let [type (base-type type-sym)]
                                   [type base-assign-impls]
                                   [(resolve type-sym) proto-assign-impls])]
    (core/when true #_(core/and (:extending-base-js-type cljs.analyzer/*cljs-warnings*)
            (js-base-type type-sym))
      #_(cljs.analyzer/warning :extending-base-js-type env
        {:current-symbol type-sym :suggested-symbol (js-base-type type-sym)}))
    `(do ~@(mapcat #(assign-impls env resolve type-sym type %) impl-map))))

