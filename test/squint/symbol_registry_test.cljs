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
  (str/replace (str (js/process.cwd) "/src/squint/core.js") "\\" "/"))

(defn- two-instances
  "Resolves to [A B]: the module at `p`, evaluated twice."
  [p]
  (let [href (.-href (url/pathToFileURL p))]
    (js/Promise.all #js [(dynamic-import (str href "?instance=a"))
                         (dynamic-import (str href "?instance=b"))])))

(defn- write-temp!
  [suffix contents]
  (let [p (str/replace (path/join (.realpathSync fs (os/tmpdir))
                                  (str "squint-registry-" (.getTime (js/Date.)) suffix))
                       "\\" "/")]
    (fs/writeFileSync p contents)
    p))

(deftest core-protocols-cross-instance
  (async done
    (->
     (.then (two-instances core-path)
            (fn [[a b]]
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
                (is (true? (.map_entry_QMARK_ a (.first a (.seq b #js {:a 1}))))))))
     (.catch (fn [err] (is false (.-message err))))
     (.finally #(done)))))

(deftest user-protocols-cross-instance
  (async done
    (let [js (-> (squint/compile-string
                  (str "(ns shapes)\n"
                       "(defprotocol IShape (-area [x]))\n"
                       "(deftype Sq [s] IShape (-area [_] (* s s)))\n"
                       "(defn make [n] (->Sq n))\n"))
                 (str/replace "squint-cljs/core.js" core-path))
          p (write-temp! ".mjs" js)]
      (->
       (.then (two-instances p)
              (fn [[a b]]
                (testing "a type from one instance dispatches through the other's protocol"
                  (is (= 9 (._area a (.make b 3))))
                  (is (= 16 (._area b (.make a 4)))))
                (testing "both instances agree on the marker symbol"
                  (is (identical? (.-__sym (.-IShape a))
                                  (.-__sym (.-IShape b)))))))
       (.catch (fn [err] (is false (.-message err))))
       (.finally (fn []
                   (fs/rmSync p)
                   (done)))))))
