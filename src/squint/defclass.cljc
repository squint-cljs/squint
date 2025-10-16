(ns squint.defclass
  (:require [clojure.string :as str]
            [clojure.walk :as walk]))

;; https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Classes
;; https://clojureverse.org/t/modern-js-with-cljs-class-and-template-literals/7450
;; https://github.com/thheller/shadow-cljs/blob/51b15dd52c74f1c504010f00cb84372bc2696a4d/src/main/shadow/cljs/modern.cljc#L19
;; https://github.com/thheller/shadow-cljs/blob/51b15dd52c74f1c504010f00cb84372bc2696a4d/src/repl/shadow/cljs/modern_test.clj#L2

(defn defclass [_ _ & body]
  `(defclass* ~@body))

(defn- parse-class [form]
  (loop [classname nil
         fields []
         constructor nil
         extends nil
         protocols []
         protocol nil
         protocol-fns []
         [head & more :as current] form]

    (let [l? (seq? head) s? (symbol? head)]

      (cond
        ;; all done
        (not (seq current))
        (-> {:classname classname
             :extends extends
             :constructor constructor
             :fields fields
             :protocols protocols}
            (cond->
                protocol
              (update :protocols conj {:protocol-name protocol :protocol-fns protocol-fns})))

        ;; SomeClass, symbol before any other form
        (and s? (nil? constructor) (empty? fields) (empty? protocols) (nil? extends) (nil? protocol) (nil? classname))
        (recur head fields constructor extends protocols protocol protocol-fns more)

        ;; (field foo default?)
        (and l? (nil? protocol) (= 'field (first head)))
        (let [field-count (count head)
              field-name (nth head 1)]
          (cond
            ;; (field foo)
            (and (= 2 field-count) (simple-symbol? field-name))
            (recur classname (conj fields {:field-form head :field-name field-name}) constructor extends protocols protocol protocol-fns more)

            ;; (field foo some-default)
            ;; FIXME: should restrict some-default to actual values, not sure expressions will work?
            (and (= 3 field-count) (simple-symbol? field-name))
            (recur classname (conj fields {:field-form head :field-name field-name :field-default (nth head 2)}) constructor extends protocols protocol protocol-fns more)
            :else
            (throw (ex-info "invalid field definition" {:form head}))))

        ;; (constructor [foo bar] ...)
        (and l? (nil? protocol) (nil? constructor) (= 'constructor (first head)) (vector? (second head)))
        (recur classname fields head extends protocols protocol protocol-fns more)

        ;; (extends SomeClass)
        (and l? (nil? protocol) (nil? extends) (= 'extends (first head)) (= 2 (count head)) (symbol? (second head)))
        (recur classname fields constructor (second head) protocols protocol protocol-fns more)

        ;; SomeProtocol start when protocol already active, save protocol, repeat current
        (and s? (some? protocol))
        (recur classname fields constructor extends (conj protocols {:protocol-name protocol :protocol-fns protocol-fns}) nil [] current)

        ;; SomeProtocol start
        s?
        (recur classname fields constructor extends protocols head [] more)

        ;; (protocol-fn [] ...)
        (and l? protocol)
        (recur classname fields constructor extends protocols protocol
               ;; this is important so that the extend-type code emits a var self__ = this;
               ;; no clue why ::ana/type controls that
               (conj protocol-fns (vary-meta head assoc :xana/type classname))
               more)

        :else
        (throw (ex-info "invalid defclass form" {:form head}))
        ))))


(defn- find-and-replace-super-call [form super? fields this-sym]
  (let [res
        (walk/prewalk
         (fn [form]
           (if-not (and (list? form) (= 'super (first form)))
             form
             `(super* {:forms ~(rest form) :fields ~fields :this-sym ~this-sym})))
         form)]
    (if (not= form res)
      res
      ;; if super call was not found, add it first
      (if super? (cons `(super*) form)
          form))))

(defn- emit-fields [env emit-fn fields]
  (let [fields-str
        (str/join "\n"
          (for [field fields
                :let [env (assoc env :context :expr)
                      default? (contains? field :field-default)
                      static (-> field :field-form first meta :static)]]
            (str (when static "static ") (munge (:field-name field))
             (if-not default? ";" (str " = " (emit-fn (:field-default field) env) ";")))))]
    (when-not (empty? fields-str)
      (str fields-str "\n"))))

(defn- emit-args [env emit-fn args]
  (let [arg-env (assoc env :context :expr :top-level false)]
    (map #(emit-fn % arg-env) args)))

(defn emit-super
  [env emit-fn {:keys [forms this-sym]}]
  (let [super-args forms]
    (str "super("
         (str/join ", " (emit-args env emit-fn super-args))
         ");"
         "const self__ = this;\n"
         (when this-sym
           (str "const " (emit-fn this-sym env) " = this;\n")))))

(defn- emit-object-fn [env emit-fn async-fn object-fn]
  (let [[fn-name arglist & body] object-fn
        [this-arg & arglist] arglist
        env (update env :var->ident (fn [vi]
                                      (-> (apply dissoc vi 'super this-arg arglist)
                                          (merge (assoc (zipmap arglist arglist)
                                                        'super "super"
                                                        this-arg (munge this-arg))))))
        mf (meta fn-name)
        async? (:async mf)
        gen? (:gen mf)
        static? (:static mf)
        env (if async? (assoc env :async async?) env)]
    (async-fn async?
              (fn []
                (str
                 (when static?
                   "static ")
                 (when async?
                   "async ")
                 (when gen? "* ")
                 (munge fn-name) "("
                 (str/join ", " (emit-args env emit-fn arglist))
                 ") { \n"
                 "const " (emit-fn this-arg env) " = this;\n"
                 "const self__ = this;"
                 (let [ret-val (last body)
                       ret-ctx (assoc env :context :return :top-level false)
                       non-ret-vals (butlast body)
                       non-ret-ctx (assoc env :context :statement :top-level false)]
                   (str (str/join (map #(str (emit-fn % non-ret-ctx) ";\n") non-ret-vals))
                        (emit-fn ret-val ret-ctx)))
                 "\n}")))))

(defn emit-class
  [env* emit-fn async-fn emit-return form]
  (let [env (assoc env* :context :statement)
        {:keys [classname extends extend constructor fields protocols] :as _all} (parse-class (rest form))
        [_ ctor-args & ctor-body] constructor
        classname* (symbol (str classname "$"))
        [this-sym & ctor-args] ctor-args
        super? (some? extends)
        ctor-body
        (find-and-replace-super-call ctor-body super? fields this-sym)
        field-syms (map :field-name fields)
        field-locals (reduce
                      (fn [m fld]
                        (assoc m fld
                               (symbol (str "self__." (munge fld)))))
                      {}
                      field-syms)
        fields-env (update env :var->ident (fn [vi]
                                             (-> (apply dissoc vi field-syms)
                                                 (merge field-locals))))
        ctor-arg-locals (cond->>  ctor-args
                           this-sym (cons this-sym))
        ctor-args-munged (zipmap ctor-arg-locals
                                 (cond->> (map munge ctor-args)
                                   this-sym (cons (munge this-sym))))
        ctor-args-env (update fields-env :var->ident (fn [vi]
                                                       (-> (apply dissoc vi ctor-arg-locals)
                                                           (merge ctor-args-munged))))
        object-fns (-> (some #(when (= 'Object (:protocol-name %)) %) protocols)
                       :protocol-fns)
        extend-form
        `(cljs.core/extend-type ~classname
           ~@(->> (for [{:keys [protocol-name protocol-fns]
                         } protocols
                        :when (not (= 'Object protocol-name))]
                    (into [protocol-name] protocol-fns))
               (mapcat identity)))]
    (str
     "class "
     (munge classname*)
     (when extends
       (str " extends "
            (emit-fn extends env)))
     " {\n"
     (emit-fields env emit-fn fields)
     (when constructor
       (str
        "  constructor(" (str/join ", " (map #(emit-fn % ctor-args-env) ctor-args)) ") {\n"
        (when-not super?
          (str "const self__ = this;\n"
               (when this-sym
                 (str "const " (emit-fn this-sym ctor-args-env) " = this;\n"))))
        (when ctor-body (emit-fn (cons 'do ctor-body) ctor-args-env))
        "  }\n"))
     (str/join "\n" (map #(emit-object-fn fields-env emit-fn async-fn %) object-fns))
     "};\n"
     (emit-fn extend-form fields-env)
     (when extend
       (str extend))
     (when true #_(:repl env)
       (emit-fn (list 'def classname (list 'js* (munge classname*))) env))
     (when (= :return (:context env*))
       (emit-return (munge classname) env*)))))

(defn process-template-arg [arg]
  (if (string? arg)
    arg
    (str "${" "~{}" "}")))

(defn js-template [_ _ tag & args]
  (let [res `(let [v# (~'js* {:context :expr} ~(str "~{}`" (str/join (map process-template-arg args))  "`") ~tag ~@(filter #(not (string? %)) args))]
               v#)]
    #_(prn :res res)
    res))


;; DONE: super must occur before anything else
;; DONE: fix build
;; DONE: fix super args
;; DONE: field defaults
;; DONE: super in method overrides https://github.com/thheller/shadow-cljs/issues/1137
;; DONE: js-literal
;; DONE: lit project as test
;; DONE: test munging of constructor + method args
;; DONE: currently constructor args are considered locals in other method bodies
;; DONE: munge method names and variable names: handle-click, etc
;; DONE: write defclass tests
;; DONE: write js-template tests
;; TODO: hiccup in js-template
