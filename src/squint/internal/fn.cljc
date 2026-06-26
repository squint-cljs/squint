;; Adapted from CLJS core.cljc. Original copyright notice:

;;   Copyright (c) Rich Hickey. All rights reserved.  The use and distribution
;;   terms for this software are covered by the Eclipse Public License
;;   1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in
;;   the file epl-v10.html at the root of this distribution.  By using this
;;   software in any fashion, you are agreeing to be bound by the terms of this
;;   license.  You must not remove this notice, or any other, from this
;;   software.

(ns squint.internal.fn
  {:clj-kondo/config '{:linters {:discouraged-var {clojure.core/gensym {:level :off}}}}})

#?(:cljs (def Exception js/Error))

(defn maybe-destructured
  [params body]
  (if (every? symbol? params)
    (cons params body)
    (loop [params params
           new-params (with-meta [] (meta params))
           lets []]
      (if params
        (if (symbol? (first params))
          (recur (next params) (conj new-params (first params)) lets)
          (let [fparam (first params)]
            (if (and (map? fparam)
                     (let [m (meta fparam)]
                       (and (not (:as fparam))
                            (or (:js m)
                                (= 'js (:tag m)))
                            (:keys fparam)
                            (empty? (dissoc fparam :keys)))))
              (recur (next params)
                     (conj new-params fparam)
                     lets)
              (let [gparam (gensym "p__")]
                (recur (next params) (conj new-params gparam)
                       (-> lets (conj fparam) (conj gparam)))))))
        `(~new-params
          (let ~lets
            ~@body))))))

(defn- multi-arity-fn [name meta fdecl _emit-var?]
  ;; native arity dispatch: each arity gets its own impl fn (which does its own
  ;; param destructuring); the facade switches on args.length and passes RAW
  ;; args through to the impl (splicing destructuring forms into the call would
  ;; emit them as literals - see variadic-fn). The variadic arity uses a native
  ;; rest slice and is stashed under squint$lang$variadic so apply can dispatch
  ;; it lazily. @__PURE__ keeps the whole def droppable when unused.
  (let [async (:async meta)
        gen (:gen meta)
        fmeta {:async async :gen gen}
        name (with-meta (munge (or name (gensym "f")))
               (assoc fmeta :squint.compiler/no-rename true))
        args-sym (gensym "args")
        rest-sym (gensym "rest")
        methods (mapv (fn [[sig & body]]
                        (let [variadic? (boolean (some '#{&} sig))
                              clean (vec (remove '#{&} sig))]
                          {:body body
                           :variadic? variadic?
                           :impl-sym (gensym "impl")
                           :fixed (if variadic? (subvec clean 0 (dec (count clean))) clean)
                           :rest-target (when variadic? (peek clean))}))
                      fdecl)
        variadic (first (filter :variadic? methods))
        maxfa (when variadic (count (:fixed variadic)))
        impl-binds (mapcat (fn [m]
                             [(:impl-sym m)
                              (with-meta
                                `(fn [~@(:fixed m) ~@(when (:rest-target m) [(:rest-target m)])]
                                   ~@(:body m))
                                fmeta)])
                           methods)
        arg-refs (fn [n] (map (fn [i] `(aget ~args-sym ~i)) (range n)))
        fixed-cases (mapcat (fn [m]
                              (let [c (count (:fixed m))]
                                [c `(~(:impl-sym m) ~@(arg-refs c))]))
                            (remove :variadic? methods))
        default-case (if variadic
                       `(let [~rest-sym (.slice ~args-sym ~maxfa)]
                          (~(:impl-sym variadic) ~@(arg-refs maxfa)
                           (if (zero? (.-length ~rest-sym)) nil ~rest-sym)))
                       `(throw (js/Error. (str "Invalid arity: " (.-length ~args-sym)))))]
    `(cljs.core/js* "/* @__PURE__ */ ~{}"
       (let [~@impl-binds
             ~name (fn [~(symbol (str "..." args-sym))]
                     (case (.-length ~args-sym)
                       ~@fixed-cases
                       ~default-case))]
         ~@(when variadic
             [`(unchecked-set ~name "squint$lang$variadic" ~(:impl-sym variadic))])
         ~name))))

(defn- variadic-fn? [fdecl]
  (and (= 1 (count fdecl))
       (some '#{&} (ffirst fdecl))))

(defn- variadic-fn [name meta [[arglist & body] :as _fdecl] _emit-var?]
  (let [async (:async meta)
        gen (:gen meta)
        ;; no-rename so the recursive self-reference from impl resolves
        name (with-meta (munge (or name (gensym "f")))
               {:squint.compiler/no-rename true :async async :gen gen})
        sig (vec (remove '#{&} arglist))
        fixed (subvec sig 0 (dec (count sig)))
        rest-target (peek sig)
        ;; the facade takes plain params and passes them through; impl does any
        ;; destructuring. Splicing the fixed params (which may be destructuring
        ;; forms) into the impl CALL would emit them as literals, not values.
        fixed-syms (mapv (fn [_] (gensym "arg")) fixed)
        rest-sym (gensym "rest")
        impl (gensym "impl")
        fmeta {:async async :gen gen}]
    ;; native rest params for direct calls; the seq-taking impl is stashed under
    ;; the squint$lang$variadic property so apply can pass an unrealized rest seq
    ;; (lazy, no spread). empty rest -> nil (CLJS-compat). @__PURE__ keeps the def
    ;; droppable when unused. only impl carries :async/:gen (it holds the
    ;; body/yields); the facade is a plain fn returning impl's result.
    `(cljs.core/js* "/* @__PURE__ */ ~{}"
       (let [~impl ~(with-meta `(fn [~@fixed ~rest-target] ~@body) fmeta)
             ~name (fn [~@fixed-syms ~(symbol (str "..." rest-sym))]
                     (~impl ~@fixed-syms
                      (if (zero? (.-length ~rest-sym)) nil ~rest-sym)))]
         (unchecked-set ~name "squint$lang$variadic" ~impl)
         ~name))))

(defn core-fn
  [&form &env & sigs]
  (let [name (if (symbol? (first sigs)) (first sigs) nil)
        sigs (if name (next sigs) sigs)
        sigs (if (vector? (first sigs))
               (list sigs)
               (if (seq? (first sigs))
                 sigs
                 ;; Assume single arity syntax
                 (throw (Exception.
                         (if (seq sigs)
                           (str "Parameter declaration "
                                (first sigs)
                                " should be a vector")
                           "Parameter declaration missing")))))
        psig (fn* [sig]
                  ;; Ensure correct type before destructuring sig
                  (when (not (seq? sig))
                    (throw (Exception.
                            (str "Invalid signature " sig
                                 " should be a list"))))
                  (let [[params & body] sig
                        _ (when (not (vector? params))
                            (throw (Exception.
                                    (if (seq? (first sigs))
                                      (str "Parameter declaration " params
                                           " should be a vector")
                                      (str "Invalid signature " sig
                                           " should be a list")))))
                        conds (when (and (next body) (map? (first body)))
                                (first body))
                        body (if conds (next body) body)
                        conds (or conds (meta params))
                        pre (:pre conds)
                        post (:post conds)
                        body (if post
                               `((let [~'% ~(if (< 1 (count body))
                                              `(do ~@body)
                                              (first body))]
                                   ~@(map (fn* [c] `(assert ~c)) post)
                                   ~'%))
                               body)
                        body (if pre
                               (concat (map (fn* [c] `(assert ~c)) pre)
                                       body)
                               body)]
                    (maybe-destructured params body)))
        m (meta name)
        mf (merge (meta &form) (meta (first &form)))
        async? (:async mf)
        gen? (:gen mf)
        m (cond-> m
            async? (assoc :async true)
            gen? (assoc :gen true))
        new-sigs (map psig sigs)]
    (cond
      (< 1 (count sigs))
      (multi-arity-fn name
                      (if false #_(comp/checking-types?)
                          (update-in m [:jsdoc] conj "@param {...*} var_args")
                          m) sigs (:def-emits-var &env))
      (variadic-fn? sigs)
      (variadic-fn name
                   (if false #_(comp/checking-types?)
                       (update-in m [:jsdoc] conj "@param {...*} var_args")
                       m) sigs (:def-emits-var &env))
      :else
      (with-meta
        (if name
          (list* 'fn* name new-sigs)
          (cons 'fn* new-sigs))
        (merge mf m)))))

(defn
  ^{:doc "Same as (def name (core/fn [params* ] exprs*)) or (def
    name (core/fn ([params* ] exprs*)+)) with any doc-string or attrs added
    to the var metadata. prepost-map defines a map with optional keys
    :pre and :post that contain collections of pre or post conditions."
    :arglists '([name doc-string? attr-map? [params*] prepost-map? body]
                [name doc-string? attr-map? ([params*] prepost-map? body)+ attr-map?])}
  core-defn [_&form _&env name & fdecl]
  ;; Note: Cannot delegate this check to def because of the call to (with-meta name ..)
  (if (instance? #?(:clj clojure.lang.Symbol :cljs Symbol) name)
    nil
    (throw
     #?(:clj (IllegalArgumentException. "First argument to defn must be a symbol")
        :cljs (js/Error. "First argument to defn must be a symbol"))))
  (let [m (if (string? (first fdecl))
            {:doc (first fdecl)}
            {})
        fdecl (if (string? (first fdecl))
                (next fdecl)
                fdecl)
        m (if (map? (first fdecl))
            (conj m (first fdecl))
            m)
        fdecl (if (map? (first fdecl))
                (next fdecl)
                fdecl)
        fdecl (if (vector? (first fdecl))
                (list fdecl)
                fdecl)
        m (if (map? (last fdecl))
            (conj m (last fdecl))
            m)
        fdecl (if (map? (last fdecl))
                (butlast fdecl)
                fdecl)
        ;; record arglists in the var metadata (fdecl is normalized above to a
        ;; seq of arities, each (params & body)), so nREPL info/eldoc can read
        ;; them back from ns-state. Inert for emission (name emits via munge).
        m (conj {:arglists (apply list (map first fdecl))} m)
        m (conj (if (meta name) (meta name) {}) m)]
    (list 'def (with-meta name m)
          ;; The fn value only needs the ::def marker and codegen hints; var
          ;; metadata like :arglists/:doc belongs on the name, not the fn value
          ;; (it would otherwise surface as runtime metadata via with-meta).
          (with-meta (cons `fn fdecl)
            (assoc (select-keys m [:async :gen :tag]) ::def true)))))

(defn core-defn- [_&form _&env name & args]
  `(clojure.core/defn ~(vary-meta name assoc :private true) ~@args))

(defn core-defmacro
  "Like defn, but the resulting function name is declared as a
  macro and will be used as a macro by the compiler when it is
  called."
  {:arglists '([name doc-string? attr-map? [params*] body]
               [name doc-string? attr-map? ([params*] body)+ attr-map?])}
  [_&form _&env name & args]
  (let [prefix (loop [p (list (vary-meta name assoc :macro true)) args args]
                 (let [f (first args)]
                   (if (string? f)
                     (recur (cons f p) (next args))
                     (if (map? f)
                       (recur (cons f p) (next args))
                       p))))
        fdecl (loop [fd args]
                (if (string? (first fd))
                  (recur (next fd))
                  (if (map? (first fd))
                    (recur (next fd))
                    fd)))
        fdecl (if (vector? (first fdecl))
                (list fdecl)
                fdecl)
        add-implicit-args (fn [fd]
                            (let [args (first fd)]
                              (cons (vec (cons '&form (cons '&env args))) (next fd))))
        add-args (fn [acc ds]
                   (if (nil? ds)
                     acc
                     (let [d (first ds)]
                       (if (map? d)
                         (conj acc d)
                         (recur (conj acc (add-implicit-args d)) (next ds))))))
        fdecl (seq (add-args [] fdecl))
        decl (loop [p prefix d fdecl]
               (if p
                 (recur (next p) (cons (first p) d))
                 d))]
    (cons `defn decl)))
