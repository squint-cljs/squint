(ns cherry.internal.fn)

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
          (let [gparam (gensym "p__")]
            (recur (next params) (conj new-params gparam)
                   (-> lets (conj (first params)) (conj gparam)))))
        `(~new-params
          (let ~lets
            ~@body))))))

(defn core-fn
  [&form sigs]
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
                           (str "Parameter declaration missing"))))))
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
        new-sigs (map psig sigs)]
    (with-meta
      (if name
        (list* 'fn* name new-sigs)
        (cons 'fn* new-sigs))
      (meta &form))))

(defn core-js-arguments []
  (list 'js* "arguments"))

(defn core-unchecked-get [obj key]
  (list 'js* "(~{}[~{}])" obj key))

(defn- multi-arity-fn [name meta fdecl emit-var?]
  (letfn [(dest-args [c]
            (map (fn [n] (core-unchecked-get (core-js-arguments) n))
                 (range c)))
          (fixed-arity [rname sig]
            (let [c (count sig)]
              [c `(. ~rname
                     (~(symbol
                        (str "cljs$core$IFn$_invoke$arity$" c))
                      ~@(dest-args c)))]))
          (fn-method [name [sig & body :as method]]
            (if
                (some '#{&} sig)
                nil #_(variadic-fn* name method false)
                ;; fix up individual :fn-method meta for
                ;; cljs.analyzer/parse 'set! :top-fn handling
                `(set!
                  (. ~(vary-meta name update :top-fn merge
                                 {:variadic? false :fixed-arity (count sig)})
                     ~(symbol (str "-cljs$core$IFn$_invoke$arity$"
                                   (count sig))))
                  (fn ~method))))]
    (let [rname    (symbol
                    ;; TODO:
                    #_(str  ana/*cljs-ns*) (str name))
          arglists (map first fdecl)
          macro?   (:macro meta)
          varsig?  #(boolean (some '#{&} %))
          {sigs false var-sigs true} (group-by varsig? arglists)
          variadic? (pos? (count var-sigs))
          variadic-params  (if variadic?
                             (cond-> (remove '#{&} (first var-sigs))
                               true count
                               macro? (- 2))
                             0)
          maxfa    (apply max
                          (concat
                           (map count sigs)
                           [(- (count (first var-sigs)) 2)]))
          mfa      (cond-> maxfa macro? (- 2))
          meta     (assoc meta
                          :top-fn
                          {:variadic? variadic?
                           :fixed-arity mfa
                           :max-fixed-arity mfa
                           :method-params (cond-> sigs #_#_macro? elide-implicit-macro-args)
                           :arglists (cond-> arglists #_#_macro? elide-implicit-macro-args)
                           :arglists-meta (doall (map meta arglists))})
          args-sym (gensym "args")
          param-counts (map count arglists)
          name     (with-meta name meta)]
      #_(when (< 1 (count var-sigs))
          (ana/warning :multiple-variadic-overloads {} {:name name}))
      #_(when (and (pos? variadic-params)
                   (not (== variadic-params (+ 1 mfa))))
          (ana/warning :variadic-max-arity {} {:name name}))
      #_(when (not= (distinct param-counts) param-counts)
          (ana/warning :overload-arity {} {:name name}))
      `(do
         (def ~name
           (fn [~'var_args]
             (case (alength ~(core-js-arguments))
               ~@(mapcat #(fixed-arity rname %) sigs)
               ~(if variadic?
                  `(let [args-arr# (array)]
                     (copy-arguments args-arr#)
                     (let [argseq# (new #_:ana/no-resolve cljs.IndexedSeq
                                        (.slice args-arr# ~maxfa) 0 nil)]
                       (. ~rname
                          (~'cljs$core$IFn$_invoke$arity$variadic
                           ~@(dest-args maxfa)
                           argseq#))))
                  (if (:macro meta)
                    `(throw (js/Error.
                             (str "Invalid arity: " (- (alength ~(core-js-arguments)) 2))))
                    `(throw (js/Error.
                             (str "Invalid arity: " (alength ~(core-js-arguments))))))))))
         ~@(map #(fn-method name %) fdecl)
         ;; optimization properties
         (set! (. ~name ~'-cljs$lang$maxFixedArity) ~maxfa)
         ~(when emit-var? `(var ~name))))))

(defn
  ^{:doc "Same as (def name (core/fn [params* ] exprs*)) or (def
    name (core/fn ([params* ] exprs*)+)) with any doc-string or attrs added
    to the var metadata. prepost-map defines a map with optional keys
    :pre and :post that contain collections of pre or post conditions."
    :arglists '([name doc-string? attr-map? [params*] prepost-map? body]
                [name doc-string? attr-map? ([params*] prepost-map? body)+ attr-map?])}
  core-defn [_&form &env name fdecl]
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
        m m #_(conj {:arglists (list 'quote (sigs fdecl))} m)
        ;; no support for :inline
                                        ;m (let [inline (:inline m)
                                        ;             ifn (first inline)
                                        ;             iname (second inline)]
                                        ;    ;; same as: (if (and (= 'fn ifn) (not (symbol? iname))) ...)
                                        ;    (if (if #?(:clj (clojure.lang.Util/equiv 'fn ifn)
                                        ;               :cljs (= 'fn ifn))
                                        ;          (if #?(:clj (instance? clojure.lang.Symbol iname)
                                        ;                 :cljs (instance? Symbol iname)) false true))
                                        ;      ;; inserts the same fn name to the inline fn if it does not have one
                                        ;      (assoc m
                                        ;        :inline (cons ifn
                                        ;                  (cons (clojure.lang.Symbol/intern
                                        ;                          (.concat (.getName ^clojure.lang.Symbol name) "__inliner"))
                                        ;                    (next inline))))
                                        ;      m))
        m (conj (if (meta name) (meta name) {}) m)]
    (cond
      ;; multi arity fn
      (< 1 (count fdecl))
      (multi-arity-fn name
                      (if false #_(comp/checking-types?)
                          (update-in m [:jsdoc] conj "@param {...*} var_args")
                          m) fdecl (:def-emits-var &env))

      #_(variadic-fn? fdecl)
      #_(variadic-fn name
                     (if (comp/checking-types?)
                       (update-in m [:jsdoc] conj "@param {...*} var_args")
                       m) fdecl (:def-emits-var &env))

      :else
      (list 'def (with-meta name m)
            ;;todo - restore propagation of fn name
            ;;must figure out how to convey primitive hints to self calls first
            (with-meta (cons `fn fdecl) m)))))
