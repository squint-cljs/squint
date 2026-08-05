;; Adapted from ClojureScript core.cljc, and from Clojure core.clj for the
;; 1.13 map directives. Original copyright notice:

;;   Copyright (c) Rich Hickey. All rights reserved.  The use and distribution
;;   terms for this software are covered by the Eclipse Public License
;;   1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in
;;   the file epl-v10.html at the root of this distribution.  By using this
;;   software in any fashion, you are agreeing to be bound by the terms of this
;;   license.  You must not remove this notice, or any other, from this
;;   software.

(ns squint.internal.destructure
  (:refer-clojure :exclude [destructure])
  (:require [clojure.string :as str]))

;; The Clojure 1.13 directives (:keys!, :select, :all, :defaults, key-form :or)
;; came in via sci's src/sci/impl/destructure.cljc, which tracks clojure/clojure
;; core.clj at dd006fb9. Diff against those when syncing.

(defn- destructure-error [msg]
  (throw #?(:clj (new Exception msg)
            :cljs (new js/Error msg))))

(defn mark-rest-args
  "Marks a binding form that receives rest args. See ADR 0008."
  [b]
  (cond-> b (map? b) (vary-meta assoc ::rest-args true)))

(defn- key-form->key
  "A key written in a binding form, as the map key squint emits. A keyword, a
  symbol and a string all munge to the same string, so :keys, :syms and :strs
  are one directive here."
  [k]
  (cond
    (keyword? k) (subs (str k) 1)
    (string? k) k
    (symbol? k) (str k)
    (and (seq? k) (= 'quote (first k))) (str (second k))
    :else k))

(defn destructure [env bindings]
  (let [gensym (:gensym env)
        bents (partition 2 bindings)
        pb (fn pb [bvec b v]
             (let [pvec
                   (fn [bvec b val]
                     (let [gvec (gensym "vec__")
                           gseq (gensym "seq__")
                           gfirst (gensym "first__")
                           has-rest (some #{'&} b)]
                       (loop [ret (let [ret (conj bvec gvec val)]
                                    (if has-rest
                                      (conj ret gseq (list `seq gvec))
                                      ret))
                              n 0
                              bs b
                              seen-rest? false]
                         (if (seq bs)
                           (let [firstb (first bs)]
                             (cond
                               (= '& firstb) (recur (pb ret (mark-rest-args (second bs)) gseq)
                                                    n
                                                    (nnext bs)
                                                    true)
                               (= :as firstb) (pb ret (second bs) gvec)
                               :else (if seen-rest?
                                       (destructure-error "Unsupported binding form, only :as can follow & parameter")
                                       (recur (pb (if has-rest
                                                    (conj ret
                                                          gfirst `(first ~gseq)
                                                          gseq `(next ~gseq))
                                                    ret)
                                                  firstb
                                                  (if has-rest
                                                    gfirst
                                                    (list `nth gvec n nil)))
                                              (inc n)
                                              (next bs)
                                              seen-rest?))))
                           ret))))
                   pmap
                   (fn [bvec b v]
                     (let [gmap (gensym "map__")
                           gignore (gensym "ignore__")
                           ;; a key-form :or key munges like any other key; a
                           ;; symbol key names a binding and is left alone
                           defaults (when-let [d (:or b)]
                                      (reduce-kv (fn [m k dv]
                                                   (assoc m (if (symbol? k) k (key-form->key k)) dv))
                                                 {} d))
                           defaults-as (:defaults b)
                           _ (when (and defaults-as (not defaults))
                               (destructure-error "Can't specify :defaults without :or"))
                           b (dissoc b :defaults)
                           select (:select b)
                           all (:all b)
                           ;; hoist :or defaults only for :defaults/:select/:all,
                           ;; so a plain :or default can still refer to a sibling
                           new-or-code (and defaults (or defaults-as select all))
                           gdefaults (when new-or-code
                                       (zipmap (keys defaults)
                                               (repeatedly #(gensym "default__"))))
                           defexpr (fn [k] (if gdefaults (gdefaults k) (defaults k)))
                           ;; :keys, :syms and :strs all munge to a string key
                           xf (fn [mk]
                                (let [mkns (namespace mk)
                                      mkn (name mk)]
                                  (if (some #(str/starts-with? mkn %) ["keys" "syms" "strs"])
                                    #(str (when-let [ns (or mkns (namespace %))] (str ns "/"))
                                          (name %))
                                    (destructure-error (str "Unsupported map directive: " mk)))))
                           ret (reduce (fn [ret e]
                                         (conj ret (val e) (defaults (key e))))
                                       bvec gdefaults)
                           ret (-> ret (conj gmap) (conj v)
                                   (conj gmap)
                                   (conj
                                    ;; kwargs, see ADR 0008
                                    (if (::rest-args (meta b))
                                      (list 'if (list 'cljs.core/nil? gmap)
                                            gmap
                                            (list 'cljs.core/seq-to-map-for-destructuring gmap))
                                      ;; sequential? minus vector? is ISeq
                                      (list 'if (list 'cljs.core/sequential? gmap)
                                            (list 'if (list 'cljs.core/vector? gmap)
                                                  gmap
                                                  (list 'cljs.core/seq-to-map-for-destructuring gmap))
                                            gmap)))
                                   ((fn [ret]
                                      (if (:as b)
                                        (conj ret (:as b) gmap)
                                        ret))))
                           bes (dissoc b :as :or :select :all)
                           localize (fn [bb] (if #?(:clj (instance? clojure.lang.Named bb)
                                                    :cljs (cljs.core/implements? INamed bb))
                                                 (with-meta (symbol nil (name bb)) (meta bb))
                                                 bb))
                           push1 (fn [ret bb bk req?]
                                   (let [getter (if req? 'cljs.core/req! 'cljs.core/get)
                                         local (localize bb)
                                         local-default? (contains? defaults local)
                                         key-default? (contains? defaults bk)
                                         bv (if (or local-default? key-default?)
                                              (if (and local-default? key-default?)
                                                (destructure-error
                                                 (str "Multiple :or defaults for same key: " bk " '" local "'"))
                                                (if req?
                                                  (destructure-error
                                                   (str "Can't supply default value for required key: " bk))
                                                  (list 'cljs.core/get gmap bk
                                                        (defexpr (if local-default? local bk)))))
                                              (list getter gmap bk))]
                                     (if (or (keyword? bb) (symbol? bb))
                                       (-> ret (conj local bv))
                                       (pb ret bb bv))))
                           retsel
                           (loop [ret ret, sel #{}, bes bes, b->k {}, sub-sels nil, sub-alls nil]
                             (if (seq bes)
                               (let [be (first bes), bb (key be), bk (val be)]
                                 (if (keyword? bb)
                                   (let [dir bb
                                         tr (xf bb)
                                         req? (str/ends-with? (name bb) "!")
                                         retsel
                                         (loop [ret ret, sel sel, bbs (seq bk), preamp? true, b->k b->k]
                                           (if (seq bbs)
                                             (let [bb (first bbs)]
                                               (if (= '& bb)
                                                 (if preamp?
                                                   (recur ret sel (next bbs) false b->k)
                                                   (destructure-error (str "& can only appear once in " dir)))
                                                 (let [_ (when (and (not preamp?) (symbol? bb))
                                                           (destructure-error
                                                            (str "'" bb "' - binding symbols can only appear before '&', use keys after")))
                                                       bk (if preamp? (tr bb) (key-form->key bb))]
                                                   (recur (if (or preamp? req?)
                                                            (push1 ret (if preamp? bb gignore) bk req?)
                                                            ret)
                                                          (conj sel bk)
                                                          (next bbs) preamp?
                                                          (if preamp? (assoc b->k (localize bb) bk) b->k)))))
                                             {:ret ret, :sel sel, :b->k b->k}))]
                                     (recur (:ret retsel) (:sel retsel) (next bes) (:b->k retsel) sub-sels sub-alls))
                                   (let [bk (key-form->key bk)
                                         subsel? (and select (map? bb))
                                         bb (if (or (not subsel?) (:select bb))
                                              bb
                                              (assoc bb :select (gensym "select__")))
                                         sub-sels (if subsel? (assoc sub-sels bk (:select bb)) sub-sels)
                                         suball? (and all (map? bb))
                                         bb (if (or (not suball?) (:all bb))
                                              bb
                                              (assoc bb :all (gensym "all__")))
                                         sub-alls (if suball? (assoc sub-alls bk (:all bb)) sub-alls)
                                         b->k (if (symbol? bb) (assoc b->k bb bk) b->k)]
                                     (recur (push1 ret bb bk false) (conj sel bk) (next bes) b->k sub-sels sub-alls))))
                               {:ret ret, :sel sel, :b->k b->k, :sub-sels sub-sels, :sub-alls sub-alls}))
                           ret (:ret retsel), sel (:sel retsel), b->k (:b->k retsel)
                           ->key (fn [x]
                                   (if (symbol? x)
                                     (let [bk (b->k x)]
                                       (when (and new-or-code (not bk))
                                         (destructure-error (str "symbol " x " in :or does not refer to a binding")))
                                       bk)
                                     (key-form->key x)))
                           dm (when defaults
                                (dissoc (zipmap (map ->key (keys gdefaults)) (vals gdefaults)) nil))
                           _ (when (and new-or-code
                                        (not= (count (select-keys dm sel)) (count defaults)))
                               (destructure-error (str "keys "
                                                       (apply disj (set (keys dm)) sel)
                                                       " appear only in :or")))
                           merged (fn [subs]
                                    (list 'cljs.core/merge
                                          (list 'cljs.core/some-vals dm)
                                          gmap
                                          (list 'cljs.core/some-vals subs)))
                           ret (if select
                                 (let [mm (gensym "mm__")]
                                   (-> ret
                                       (conj mm (merged (:sub-sels retsel)))
                                       (conj select (list 'if mm
                                                          (list 'cljs.core/select-keys mm (vec sel))
                                                          nil))))
                                 ret)
                           ret (if all
                                 (conj ret all (merged (:sub-alls retsel)))
                                 ret)
                           ret (if defaults-as (conj ret defaults-as dm) ret)]
                       ret))]
               (cond
                 (symbol? b) (-> bvec (conj (if (namespace b) (symbol (name b)) b)) (conj v))
                 (keyword? b) (-> bvec (conj (symbol (name b))) (conj v))
                 (vector? b) (pvec bvec b v)
                 (map? b) (pmap bvec b v)
                 :else (destructure-error (str "Unsupported binding form: " b)))))
        process-entry (fn [bvec b] (pb bvec (first b) (second b)))
        ret (if (every? symbol? (map first bents))
              bindings
              (if-let [kwbs (seq (filter #(keyword? (first %)) bents))]
                (destructure-error (str "Unsupported binding key: " (ffirst kwbs)))
                (reduce process-entry [] bents)))]
    ret))

(defn core-let
  [env bindings body]
  `(cljs.core/let* ~(destructure env bindings) ~@body))
