(ns squint.symbol-registry-test
  "Cross-instance protocol dispatch. A page can end up with two evaluated copies
   of the same module - a CDN bundle that inlines squint next to an importmap
   copy, an npm duplicate, a bundler that fails to dedupe. Symbol() mints a fresh
   value per instantiation, so copies were mutually invisible; Symbol.for keys
   them off a global registry. These tests load one module twice (a distinct
   query string makes Node treat it as a separate instance) and dispatch across."
  (:require
   ["fs" :as fs]
   ["node:url" :as url]
   ["os" :as os]
   ["path" :as path]
   [clojure.string :as str]
   [clojure.test :refer [async deftest is testing]]
   [shadow.esm :refer [dynamic-import]]
   [squint.compiler :as squint]))

(def ^:private core-path
  (path/join (js/process.cwd) "src" "squint" "core.js"))

;; a generated module imports core by URL, not by path: the ESM loader rejects a
;; bare Windows path ("Received protocol 'd:'")
(def ^:private core-url
  (.-href (url/pathToFileURL core-path)))

(defn- two-instances
  "Resolves to [A B]: the module at `p`, evaluated twice."
  [p]
  (let [href (.-href (url/pathToFileURL p))]
    (js/Promise.all #js [(dynamic-import (str href "?instance=a"))
                         (dynamic-import (str href "?instance=b"))])))

(defn- write-temp!
  [suffix contents]
  (let [p (path/join (.realpathSync fs (os/tmpdir))
                     (str "squint-registry-" (.getTime (js/Date.)) suffix))]
    (fs/writeFileSync p contents)
    p))

(deftest core-protocols-cross-instance
  (async done
    (->
     (.then (two-instances core-path)
            (fn [[a b]]
              (let [^js a a
                    ^js b b]
                (is (not (identical? a b)) "query string did not produce a second instance")
                (testing "protocol slots on values created by the other instance"
                  ;; IDeref/IReset/ISwap live on the Atom itself, as own properties
                  (let [atm (.atom b #js {:x 1})]
                    (is (= 1 (.-x (.deref a atm))))
                    (.swap_BANG_ a atm (fn [m] (.assoc a m "y" 2)))
                    (is (= 2 (.-y (.deref a atm))))
                    (.reset_BANG_ a atm #js {:z 3})
                    (is (= 3 (.-z (.deref a atm))))))
                (testing "TYPE_TAG dispatch: a list must stay a list"
                  ;; without a shared tag it degrades to a plain array and conj appends
                  (let [lst (.list b 1 2)]
                    (is (= [0 1 2] (vec (.conj a lst 0))))
                    (is (= "(1, 2)" (.pr_str a lst)))))
                (testing "SORTED_TAG dispatch"
                  (is (= 4 (.count a (.conj a (.sorted_set b 3 1 2) 4)))))
                (testing "MAP_ENTRY tag"
                  (is (true? (.map_entry_QMARK_ a (.first a (.seq b #js {:a 1})))))))))
     (.catch (fn [err] (is false (.-message err))))
     (.finally #(done)))))

(deftest user-protocols-cross-instance
  (async done
    (let [js (-> (squint/compile-string
                  (str "(ns shapes)\n"
                       "(defprotocol IShape (-area [x]))\n"
                       "(deftype Sq [s] IShape (-area [_] (* s s)))\n"
                       "(defn make [n] (->Sq n))\n"))
                 (str/replace "squint-cljs/core.js" core-url))
          p (write-temp! ".mjs" js)]
      (is (str/includes? js "file://") "core must be imported by URL, not by path")
      (->
       (.then (two-instances p)
              (fn [[a b]]
                (let [^js a a
                      ^js b b]
                  (testing "a type from one instance dispatches through the other's protocol"
                    (is (= 9 (._area a (.make b 3))))
                    (is (= 16 (._area b (.make a 4)))))
                  (testing "both instances agree on the marker symbol"
                    (is (identical? (.-__sym (.-IShape a))
                                    (.-__sym (.-IShape b)))))
                  (testing "the slot key is the qualified method name, as written"
                    (is (identical? (js/Symbol.for "shapes/-area")
                                    (.-IShape__area a)))))))
       (.catch (fn [err] (is false (.-message err))))
       (.finally (fn []
                   (fs/rmSync p)
                   (done)))))))

(def ^:private core-registry-keys
  "Every Symbol.for key in core.js. These are permanent: two evaluated copies of
   core agree only when the strings match, so a change here breaks interop
   between squint versions. Update this list only on purpose."
  ["squint.core/-add-watch" "squint.core/-as-transient" "squint.core/-assoc"
   "squint.core/-assoc!" "squint.core/-clj->js" "squint.core/-conj"
   "squint.core/-conj!" "squint.core/-contains-key?" "squint.core/-count"
   "squint.core/-deref" "squint.core/-disjoin" "squint.core/-disjoin!"
   "squint.core/-dissoc" "squint.core/-dissoc!" "squint.core/-empty"
   "squint.core/-equiv" "squint.core/-hash" "squint.core/-kv-reduce"
   "squint.core/-lookup" "squint.core/-meta" "squint.core/-notify-watches"
   "squint.core/-nth" "squint.core/-peek" "squint.core/-persistent!"
   "squint.core/-pop" "squint.core/-pop!" "squint.core/-pr-writer"
   "squint.core/-remove-watch" "squint.core/-reset!" "squint.core/-seq"
   "squint.core/-swap!" "squint.core/-with-meta" "squint.core/-write"
   "squint.core/IAssociative" "squint.core/IAtom" "squint.core/ICollection"
   "squint.core/ICounted" "squint.core/IDeref"
   "squint.core/IEditableCollection" "squint.core/IEmptyableCollection"
   "squint.core/IEncodeJS" "squint.core/IEquiv" "squint.core/IHash"
   "squint.core/IIndexed" "squint.core/IIterable" "squint.core/IKVReduce"
   "squint.core/ILookup" "squint.core/IMap" "squint.core/IMeta"
   "squint.core/IPrintWithWriter" "squint.core/IRecord" "squint.core/IReset"
   "squint.core/ISeqable" "squint.core/ISet" "squint.core/IStack"
   "squint.core/ISwap" "squint.core/ITransientAssociative"
   "squint.core/ITransientCollection" "squint.core/ITransientMap"
   "squint.core/ITransientSet" "squint.core/ITransientVector"
   "squint.core/IVector" "squint.core/IWatchable" "squint.core/IWithMeta"
   "squint.core/IWriter" "squint.core/map-entry" "squint.core/sorted"
   "squint.core/type"])

(deftest core-registry-keys-are-stable
  (let [found (->> (re-seq #"Symbol\.for\('([^']*)'\)" (fs/readFileSync core-path "utf8"))
                   (map second)
                   sort
                   vec)
        known (set core-registry-keys)]
    (testing "no key was added or renamed"
      (is (empty? (remove known found))))
    (testing "no key was removed"
      (is (empty? (remove (set found) core-registry-keys))))))
