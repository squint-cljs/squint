(ns squint.internal.macros.defclass
  (:require [clojure.string :as str]))

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

(defn emit-class
  [env emit-fn form]
  (let [{:keys [classname extends extend constructor]} (parse-class (rest form))
        [_ ctor-args & ctor-body] constructor]
    (str
     "class "
     (emit-fn classname env)
     (when extends
       (str " extends "
            (emit-fn extends env)))
     (str " {\n")

     (str "  constructor(" (str/join ", " ctor-args) ") {\n")
     (str ctor-body)
     (str "  }\n")
     (str "};\n")
     (when extend
       (str extend)))))


