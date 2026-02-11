(ns squint.anf
  "ANF (A-Normal Form) transformation on s-expressions.
   Lifts IIFE-causing forms (let*, loop*, try, letfn*) out of
   expression position to avoid IIFE generation in the compiler.

   For example:
     (f (let [x 1] (inc x)))
   becomes:
     (let* [x 1 G__1 (inc x)] (f G__1))

   Expects ::get-expander in env for macro expansion.
   Tracks locals to avoid expanding locally-bound symbols.

   Adapted from cljs.anf for use in squint.")

;; Special forms that should not be macroexpanded
(def ^:private specials
  '#{if def fn* do let* loop* letfn* throw try recur new set!
     ns deftype* defrecord* . js* & quote case* var
     js/await js-await return delete while break
     squint-compiler-jsx squint-compiler-html squint-compiler-js-map
     squint.defclass/defclass* squint.defclass/super*
     squint.impl/for-of squint.impl/defonce})

(defn- preserve-meta
  "Transfer metadata from original form to rebuilt form.
   Merges metadata so inner type tags are not overwritten."
  [original rebuilt]
  (if-let [m (meta original)]
    (if #?(:clj  (instance? clojure.lang.IObj rebuilt)
           :cljs (implements? IWithMeta rebuilt))
      (with-meta rebuilt (merge (meta rebuilt) m))
      rebuilt)
    rebuilt))

(defn- bodies->form
  "Wrap multiple body forms: single form returned as-is, multiple wrapped in do."
  [bodies]
  (if (= 1 (count bodies))
    (first bodies)
    (cons 'do bodies)))

(defn- needs-lifting?
  "Returns true if form would cause an IIFE when compiled in expression position."
  [form]
  (and (seq? form)
       (contains? #{'let* 'loop* 'try 'letfn*} (first form))))

(defn- do-with-statements?
  "Returns true if form is a do with 2+ sub-forms (causes IIFE in expr position)."
  [form]
  (and (seq? form)
       (= 'do (first form))
       (> (count (rest form)) 1)))

(defn- if-with-iife-branch?
  "Returns true if form is an if where any branch needs-lifting? or is
   itself an if-with-iife-branch? (handles nested if with IIFE branches)."
  [form]
  (and (seq? form)
       (= 'if (first form))
       (let [[_ _test then else] form]
         (or (needs-lifting? then)
             (needs-lifting? else)
             (do-with-statements? then)
             (do-with-statements? else)
             (if-with-iife-branch? then)
             (if-with-iife-branch? else)))))

(declare transform)

(defn- replace-sym
  "Replace all occurrences of symbol old with new in form."
  [form old new]
  (cond
    (and (symbol? form) (= form old)) new
    (seq? form) (with-meta (doall (map #(replace-sym % old new) form)) (meta form))
    (vector? form) (mapv #(replace-sym % old new) form)
    (map? form) (into {} (map (fn [[k v]] [k (replace-sym v old new)]) form))
    (set? form) (set (map #(replace-sym % old new) form))
    :else form))

(defn- rename-inner-bindings
  "Rename binding symbols that conflict with existing locals to gensyms.
   Returns [new-bindings new-body] with conflicting references substituted."
  [locals inner-bindings body]
  (let [pairs (partition 2 inner-bindings)]
    (loop [pairs pairs
           renames {}
           out-bindings []]
      (if-let [[sym init] (first pairs)]
        (let [init (reduce-kv (fn [form old new] (replace-sym form old new))
                     init renames)
              conflicts? (contains? locals sym)
              new-sym (if conflicts? (gensym (str (name sym) "__")) sym)
              renames (if conflicts? (assoc renames sym new-sym) renames)]
          (recur (rest pairs) renames (conj out-bindings new-sym init)))
        (let [new-body (reduce-kv (fn [form old new] (replace-sym form old new))
                         body renames)]
          [out-bindings new-body])))))

(defn- flatten-nested-lets
  "Recursively extract bindings from nested let* bodies.
   Returns [all-bindings final-body] where final-body is not a let*."
  [locals bindings body]
  (if (and (seq? body) (= 'let* (first body)))
    (let [[_ inner-bindings & inner-body] body
          body-form (bodies->form inner-body)
          [renamed-bindings renamed-body] (rename-inner-bindings locals inner-bindings body-form)
          inner-syms (take-nth 2 renamed-bindings)]
      (flatten-nested-lets (into locals (set inner-syms))
                           (into bindings renamed-bindings)
                           renamed-body))
    [bindings body]))

(defn- wrap-in-assign
  "Wrap the result of form in a js* assignment to sym.
   For let*, places the assignment on the body's last expression
   so the let* stays in statement position (no IIFE).
   For if, pushes the assignment into each branch to keep them
   in statement position.
   Renames inner bindings that shadow sym to avoid wrong assignment target."
  [sym form]
  (cond
    (and (seq? form) (= 'let* (first form)))
    (let [[_ bindings & body] form
          binding-syms (set (take-nth 2 bindings))
          body-form (if (= 1 (count body)) (first body) (cons 'do body))
          ;; Rename any inner bindings that shadow the assignment target
          [bindings body-form] (if (contains? binding-syms sym)
                                 (rename-inner-bindings #{sym} bindings body-form)
                                 [bindings body-form])
          body-vec (if (and (seq? body-form) (= 'do (first body-form)))
                     (vec (rest body-form))
                     [body-form])
          last-expr (peek body-vec)
          init-stmts (pop body-vec)]
      (apply list 'let* (vec bindings)
        (conj init-stmts (wrap-in-assign sym last-expr))))

    (and (seq? form) (= 'if (first form)))
    (let [[_ test then else] form]
      (if (> (count form) 3)
        (list 'if test (wrap-in-assign sym then) (wrap-in-assign sym else))
        (list 'if test (wrap-in-assign sym then))))

    (and (seq? form) (= 'do (first form)))
    (let [do-forms (vec (rest form))
          init-stmts (pop do-forms)
          last-expr (peek do-forms)]
      (apply list 'do (conj init-stmts (wrap-in-assign sym last-expr))))

    :else
    (list 'js* "(~{} = ~{})" sym form)))

(defn- form->assign-stmts
  "Convert an IIFE-causing form into a sequence of statements ending with
   an assignment to assign-sym. For if-with-iife-branch, wraps each branch.
   For do-with-statements, hoists statements and assigns the last expression."
  [assign-sym form]
  (cond
    (if-with-iife-branch? form)
    (let [[_ test then else] form]
      [(if (> (count form) 3)
         (list 'if test
               (wrap-in-assign assign-sym then)
               (wrap-in-assign assign-sym else))
         (list 'if test
               (wrap-in-assign assign-sym then)))])

    (do-with-statements? form)
    (let [do-forms (vec (rest form))
          stmts (pop do-forms)
          last-expr (peek do-forms)]
      (conj (vec stmts) (wrap-in-assign assign-sym last-expr)))))

(defn- lift-args
  "Given a list of transformed args, extract any IIFE-causing forms into
   let* bindings. Returns [bindings stmts new-args]."
  [locals args]
  (reduce
    (fn [[bindings stmts new-args] arg]
      (let [[stmts arg] (if (do-with-statements? arg)
                           (let [do-forms (vec (rest arg))
                                 do-stmts (pop do-forms)
                                 last-expr (peek do-forms)]
                             [(into stmts do-stmts) last-expr])
                           [stmts arg])]
        (cond
          (needs-lifting? arg)
          (if (= 'let* (first arg))
            ;; Flatten let*: hoist its bindings, use body directly if trivial
            (let [[_ inner-bindings & body] arg
                  body-form (bodies->form body)
                  all-locals (into locals (set (take-nth 2 bindings)))
                  [renamed-bindings renamed-body] (rename-inner-bindings all-locals inner-bindings body-form)]
              (cond
                (needs-lifting? renamed-body)
                (let [result-sym (gensym "anf__")]
                  [(-> bindings
                       (into renamed-bindings)
                       (conj result-sym renamed-body))
                   stmts
                   (conj new-args result-sym)])
                (if-with-iife-branch? renamed-body)
                (let [tmp (vary-meta (gensym "anf__") assoc :mutable true)]
                  [(-> bindings
                       (into renamed-bindings)
                       (conj tmp (list 'js* "void 0")))
                   (into stmts (form->assign-stmts tmp renamed-body))
                   (conj new-args tmp)])
                (do-with-statements? renamed-body)
                (let [do-forms (vec (rest renamed-body))
                      do-stmts (pop do-forms)
                      last-expr (peek do-forms)]
                  [(into bindings renamed-bindings)
                   (into stmts do-stmts)
                   (conj new-args last-expr)])
                :else
                [(into bindings renamed-bindings)
                 stmts
                 (conj new-args renamed-body)]))
            ;; Other IIFE-causing forms: bind whole thing to gensym
            (let [result-sym (gensym "anf__")]
              [(conj bindings result-sym arg)
               stmts
               (conj new-args result-sym)]))

          (if-with-iife-branch? arg)
          (let [tmp (vary-meta (gensym "anf__") assoc :mutable true)
                [_ test then else] arg]
            [(conj bindings tmp (list 'js* "void 0"))
             (conj stmts (if (> (count arg) 3)
                           (list 'if test
                                 (wrap-in-assign tmp then)
                                 (wrap-in-assign tmp else))
                           (list 'if test
                                 (wrap-in-assign tmp then))))
             (conj new-args tmp)])

          :else
          [bindings stmts (conj new-args arg)])))
    [[] [] []]
    args))

(defn- transform-args
  "Transform function call args. If any transformed arg is IIFE-causing
   or is an if with IIFE branches, extract into surrounding let* bindings."
  [env locals form op args]
  (let [transformed (doall (map #(transform env locals %) args))]
    (if (or (some needs-lifting? transformed)
            (some if-with-iife-branch? transformed)
            (some do-with-statements? transformed))
      (let [[bindings stmts new-args] (lift-args locals transformed)]
        (apply list 'let* (vec bindings)
               (concat stmts [(preserve-meta form (cons op new-args))])))
      (preserve-meta form (cons op transformed)))))

(declare transform-let*)

(defn- declare-assign-let*
  "Common pattern for declare-assign in transform-let*."
  [env current-locals sym acc extra-bindings extra-syms init-form pairs body]
  (let [shadowed? (contains? current-locals sym)
        assign-sym (vary-meta (if shadowed? (gensym (str (name sym) "__")) sym) assoc :mutable true)
        new-locals (into (conj current-locals assign-sym) extra-syms)
        stmts (form->assign-stmts assign-sym init-form)
        remaining (vec (mapcat identity (rest pairs)))
        [remaining body] (if shadowed?
                           [(mapv #(replace-sym % sym assign-sym) remaining)
                            (map #(replace-sym % sym assign-sym) body)]
                           [remaining body])
        inner (if (seq remaining)
                (transform-let* env new-locals remaining body)
                (let [t-body (doall (map #(transform env new-locals %) body))]
                  (bodies->form t-body)))]
    (apply list 'let* (vec (-> acc (into extra-bindings) (conj assign-sym (list 'js* "void 0"))))
           (concat stmts
                   (if (and (seq? inner) (= 'do (first inner)))
                     (rest inner)
                     [inner])))))

(defn- transform-let*
  "Transform let* form: recursively transform binding inits and body.
   Flattens nested let* in binding inits to avoid IIFEs."
  [env locals bindings body]
  (let [pairs (partition 2 bindings)]
    (loop [pairs pairs
           acc []
           current-locals locals]
      (if-let [[sym init] (first pairs)]
        (let [t-init (transform env current-locals init)]
          (cond
            ;; Flatten nested let*
            (and (seq? t-init) (= 'let* (first t-init)))
            (let [[_ inner-bindings & inner-body] t-init
                  body-form (bodies->form inner-body)
                  [initial-bindings initial-body] (rename-inner-bindings current-locals inner-bindings body-form)
                  [renamed-bindings renamed-body] (flatten-nested-lets
                                                    (into current-locals (set (take-nth 2 initial-bindings)))
                                                    (vec initial-bindings)
                                                    initial-body)
                  inner-syms (take-nth 2 renamed-bindings)]
              (if (or (if-with-iife-branch? renamed-body)
                      (do-with-statements? renamed-body))
                (declare-assign-let* env current-locals sym acc
                  renamed-bindings inner-syms renamed-body pairs body)
                (recur (rest pairs)
                       (-> acc (into renamed-bindings) (conj sym renamed-body))
                       (into (conj current-locals sym) inner-syms))))

            ;; If with IIFE branches or do-with-statements → declare-assign
            (or (if-with-iife-branch? t-init)
                (do-with-statements? t-init))
            (declare-assign-let* env current-locals sym acc
              [] [] t-init pairs body)

            ;; Normal binding
            :else
            (recur (rest pairs)
                   (conj acc sym t-init)
                   (conj current-locals sym))))
        ;; Done — build final let*
        (let [t-body (doall (map #(transform env current-locals %) body))]
          (apply list 'let* (vec acc) t-body))))))

(defn- try-macroexpand-1
  "Attempt to macroexpand form once using ::get-expander from env.
   Returns original form if no expander or expansion fails."
  [env locals form]
  (if-let [get-exp (::get-expander env)]
    (let [op (first form)
          ;; Merge ANF-tracked locals into :locals so macros see them
          env (update env :locals
                (fn [m]
                  (reduce (fn [m sym]
                            (if (contains? m sym) m (assoc m sym {:name sym})))
                    (or m {}) locals)))]
      (if-let [mac (when (symbol? op) (get-exp op env))]
        (try
          (let [#?@(:cljs [mac (or (.-afn ^js mac) mac)])
                expanded (apply mac form env (rest form))]
            expanded)
          (catch #?(:clj Throwable :cljs :default) _
            form))
        form))
    form))

(defn transform
  "Walk an s-expression and lift IIFE-causing forms out of expression position.
   Macroexpands forms using ::get-expander from env.

   env    - compiler env with ::get-expander for expansion
   locals - set of locally-bound symbols (not macroexpanded)"
  [env locals form]
  (cond
    (seq? form)
    (if-not (seq form)
      form ;; empty list — return as-is
      (let [op (first form)
            ;; Try to macroexpand if not a special form and not locally bound
            expanded (if (and (symbol? op)
                             (not (contains? specials op))
                             (not (contains? locals op)))
                      (try-macroexpand-1 env locals form)
                      form)]
        (if (not= expanded form)
          ;; Macro expanded — recurse on expanded form, preserving original metadata
          (preserve-meta form (transform env locals expanded))
          ;; No expansion — handle by form type
          (case op
            let* (let [[_ bindings & body] form]
                   (transform-let* env locals bindings body))
            loop* (let [[_ bindings & body] form
                        pairs (partition 2 bindings)
                        [new-bindings new-locals]
                        (reduce
                          (fn [[acc current-locals] [sym init]]
                            [(conj acc sym (transform env current-locals init))
                             (conj current-locals sym)])
                          [[] locals]
                          pairs)
                        t-body (doall (map #(transform env new-locals %) body))]
                    (preserve-meta form (apply list 'loop* (vec new-bindings) t-body)))
            do (let [forms (doall (map #(transform env locals %) (rest form)))]
                 (if (= 1 (count forms))
                   (first forms)
                   (preserve-meta form (cons 'do forms))))
            if (if (< (count form) 3)
                 form
                 (let [[_ test then else] form
                       t-test (transform env locals test)
                       t-then (transform env locals then)
                       has-else (> (count form) 3)
                       t-else (when has-else (transform env locals else))]
                   (if (or (needs-lifting? t-test) (if-with-iife-branch? t-test) (do-with-statements? t-test))
                     (let [[bindings stmts [new-test]] (lift-args locals [t-test])
                           if-form (preserve-meta form
                                     (if has-else
                                       (list 'if new-test t-then t-else)
                                       (list 'if new-test t-then)))]
                       (apply list 'let* (vec bindings)
                              (concat stmts [if-form])))
                     (preserve-meta form
                       (if has-else
                         (list 'if t-test t-then t-else)
                         (list 'if t-test t-then))))))
            fn* form
            quote form
            try form
            case* form
            letfn* form
            deftype* form
            ns form
            def form
            while form
            squint.impl/for-of form
            ;; General expression: function call or other
            (transform-args env locals form op (rest form))))))

    (vector? form)
    (let [transformed (doall (map #(transform env locals %) form))]
      (if (or (some needs-lifting? transformed)
              (some if-with-iife-branch? transformed)
              (some do-with-statements? transformed))
        (let [[bindings stmts new-elems] (lift-args locals transformed)]
          (apply list 'let* (vec bindings)
                 (concat stmts [(vec new-elems)])))
        (vec transformed)))

    :else form))
