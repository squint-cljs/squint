;; Adapted from CLJS core.cljc. Original copyright notice:

;;   Copyright (c) Rich Hickey. All rights reserved.  The use and distribution
;;   terms for this software are covered by the Eclipse Public License
;;   1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in
;;   the file epl-v10.html at the root of this distribution.  By using this
;;   software in any fashion, you are agreeing to be bound by the terms of this
;;   license.  You must not remove this notice, or any other, from this
;;   software.

(ns squint.internal.destructure
  (:refer-clojure :exclude [destructure]))

(defn destructure [bindings]
  (let [bents (partition 2 bindings)
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
                               (= firstb '&) (recur (pb ret (second bs) gseq)
                                                    n
                                                    (nnext bs)
                                                    true)
                               (= firstb :as) (pb ret (second bs) gvec)
                               :else (if seen-rest?
                                       (throw #?(:clj (new Exception "Unsupported binding form, only :as can follow & parameter")
                                                 :cljs (new js/Error "Unsupported binding form, only :as can follow & parameter")))
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
                     (let [m (meta b)
                           js-keys? true #_(or (:js m)
                                        (= 'js (:tag m)))
                           gmap (gensym "map__")
                           defaults (:or b)]
                       (loop [ret (-> bvec (conj gmap) (conj v)
                                      #_#_(conj gmap) (conj gmap)
                                      ((fn [ret]
                                         (if (:as b)
                                           (conj ret (:as b) gmap)
                                           ret))))
                              bes (let [transforms
                                        (reduce
                                         (fn [transforms mk]
                                           (if (keyword? mk)
                                             (let [mkns (namespace mk)
                                                   mkn (name mk)]
                                               (cond
                                                 js-keys? (assoc transforms mk #(subs (str (keyword (or mkns (namespace %)) (name %))) 1))
                                                 (= mkn "keys") (assoc transforms mk #(keyword (or mkns (namespace %)) (name %)))
                                                 #_#_(= mkn "syms") (assoc transforms mk #(list `quote (symbol (or mkns (namespace %)) (name %))))
                                                 #_#_(= mkn "strs") (assoc transforms mk str)
                                                 :else transforms))
                                             transforms))
                                         {}
                                         (keys b))]
                                    (reduce
                                     (fn [bes entry]
                                       (reduce #(assoc %1 %2 ((val entry) %2))
                                               (dissoc bes (key entry))
                                               ((key entry) bes)))
                                     (dissoc b :as :or)
                                     transforms))]
                         (if (seq bes)
                           (let [bb (key (first bes))
                                 bk (val (first bes))
                                 local (if #?(:clj  (instance? clojure.lang.Named bb)
                                              :cljs (cljs.core/implements? INamed bb))
                                         (with-meta (symbol nil (name bb)) (meta bb))
                                         bb)
                                 bv (if (contains? defaults local)
                                      (list 'cljs.core/get gmap bk (defaults local))
                                      (list 'cljs.core/get gmap bk))]
                             (recur
                              (if (or (keyword? bb) (symbol? bb)) ;(ident? bb)
                                (-> ret (conj local bv))
                                (pb ret bb bv))
                              (next bes)))
                           ret))))]
               (cond
                 (symbol? b) (-> bvec (conj (if (namespace b) (symbol (name b)) b)) (conj v))
                 (keyword? b) (-> bvec (conj (symbol (name b))) (conj v))
                 (vector? b) (pvec bvec b v)
                 (map? b) (pmap bvec b v)
                 :else (throw
                        #?(:clj (new Exception (str "Unsupported binding form: " b))
                           :cljs (new js/Error (str "Unsupported binding form: " b)))))))
        process-entry (fn [bvec b] (pb bvec (first b) (second b)))
        ret (if (every? symbol? (map first bents))
              bindings
              (if-let [kwbs (seq (filter #(keyword? (first %)) bents))]
                (throw
                 #?(:clj (new Exception (str "Unsupported binding key: " (ffirst kwbs)))
                    :cljs (new js/Error (str "Unsupported binding key: " (ffirst kwbs)))))
                (reduce process-entry [] bents)))]
    ret))

(defn core-let
  [bindings body]
  #_(assert-args let
                 (vector? bindings) "a vector for its binding"
                 (even? (count bindings)) "an even number of forms in binding vector")
  `(cljs.core/let* ~(destructure bindings) ~@body))
