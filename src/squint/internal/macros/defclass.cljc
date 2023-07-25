(ns squint.internal.macros.defclass
  (:require [clojure.string :as str]
            [clojure.walk :as walk]))

;; https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Classes
;; https://clojureverse.org/t/modern-js-with-cljs-class-and-template-literals/7450
;; https://github.com/thheller/shadow-cljs/blob/51b15dd52c74f1c504010f00cb84372bc2696a4d/src/main/shadow/cljs/modern.cljc#L19
;; https://github.com/thheller/shadow-cljs/blob/51b15dd52c74f1c504010f00cb84372bc2696a4d/src/repl/shadow/cljs/modern_test.clj#L2

(defn defclass [_ _ & body]
  `(~'defclass* ~@body))

(defn parse-class [form]
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


(defn find-and-replace-super-call [form super?]
  (let [res
        (walk/prewalk
         (fn [form]
           (if-not (and (list? form) (= 'super (first form)))
             form
             `(super* ~@(rest form))))
         form)]
    (if (not= form res)
      res
      ;; if super call was not found, add it first
      (if super? (cons `(super*) form)
          form))))

(defn emit-super
  [env emit-fn form]
  "super()")

(defn emit-class
  [env emit-fn form]
  (let [{:keys [classname extends extend constructor fields protocols] :as all} (parse-class (rest form))
        _ (prn :all all)
        [_ ctor-args & ctor-body] constructor
        _ (assert (pos? (count ctor-args)) "contructor requires at least one argument name for this")

        [this-sym & ctor-args] ctor-args
        _ (assert (symbol? this-sym) "can't destructure first constructur argument")
        super? (some? extends)
        ctor-body
        (find-and-replace-super-call ctor-body super?)
        #_#_arg-syms (vec (take (count ctor-args) (repeatedly gensym)))
        field-syms (map :field-name fields)
        locals (reduce
                (fn [m fld]
                  (assoc m fld
                         (symbol (str "self__." fld))))
                {classname (munge (gensym classname))}
                field-syms)
        ctor-locals
        (reduce-kv
         (fn [locals _idx fld]
           ;; FIXME: what should fn args locals look like?
           (assoc locals fld (munge (gensym fld))))
         ;; pretty sure thats wrong but works in our favor
         ;; since accessing this before super() is invalid
         ;; and this kinda ensures that
         (assoc locals this-sym "__self")
         {})
        ctor-env (update env :var->ident merge ctor-locals)
        extend-form
        `(cljs.core/extend-type ~classname
           ~@(->> (for [{:keys [protocol-name protocol-fns]} protocols]
                    (into [protocol-name] protocol-fns))
               (mapcat identity)))]
    (str
     "class "
     (emit-fn classname env)
     (when extends
       (str " extends "
            (emit-fn extends env)))
     (str " {\n")

     (str "  constructor(" (str/join ", " ctor-args) ") {\n")
     (str "const self__ = this;\n")
     (str (when ctor-body (emit-fn (cons 'do ctor-body) ctor-env)))
     (str "  }\n")
     (str "};\n")
     (str (emit-fn extend-form env))
     (when extend
       (str extend)))))


