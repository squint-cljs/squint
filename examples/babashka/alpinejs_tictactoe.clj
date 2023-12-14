(ns alpinejs-tictactoe
  (:require [babashka.deps :as deps]
            [hiccup2.core :as h]
            [org.httpkit.server :as srv]
            [cheshire.core :as json]))

(deps/add-deps '{:deps {io.github.squint-cljs/squint {:local/root "../.."}}})

(require '[squint.compiler :as squint])

(def state (atom nil))
(defn ->js [form]
  (let [res (squint.compiler/compile-string* (str form) {:core-alias "$squint_core"})]
    (reset! state res)
    (h/raw (:body res))))

(defn page [req]
  (when (= "/" (:uri req))
    {:body (str (h/html
                 [:html
                  [:head
                   [:script {:type "importmap"}
                    (h/raw
                     (json/generate-string
                      {:imports
                       {"squint-cljs/src/squint/core.js" "https://cdn.jsdelivr.net/npm/squint-cljs@0.4.81/src/squint/core.js"
                        "squint-cljs/src/squint/string.js" "https://cdn.jsdelivr.net/npm/squint-cljs@0.4.81/src/squint/string.js"}}))]
                   [:title "Squint"]]
                  [:body {:x-data
                          (->js '{:grid [["-" "-" "-"]
                                         ["-" "-" "-"]
                                         ["-" "-" "-"]]
                                  :player "X"
                                  :winner nil
                                  :otherPlayer (fn [player]
                                                 (if (= player "X") "O" "X"))
                                  :getWinner (let [get-board-cell (fn [grid i j]
                                                                    (get-in grid [i j]))
                                                   winner-in-rows? (fn [board player]
                                                                     (some (fn [row] (every? (fn [c] (= c player)) row)) board)
                                                                     )
                                                   transposed (fn [board] (vec (apply map vector board)))
                                                   winner-in-cols? (fn [board player]
                                                                     (winner-in-rows? (transposed board) player))
                                                   winner-in-diagonals? (fn [board player]
                                                                          (let [diag-coords [[[0 0] [1 1] [2 2]]
                                                                                             [[0 2] [1 1] [2 0]]]]
                                                                            (some (fn [coords]
                                                                                    (every? (fn [coord]
                                                                                              (= player (apply get-board-cell board coord)))
                                                                                            coords))
                                                                                  diag-coords)))]
                                               (fn isWinner
                                                 ([board]
                                                  (or (isWinner board "X")
                                                      (isWinner board "O")))
                                                 ([board player]
                                                  (if (or (winner-in-rows? board player)
                                                          (winner-in-cols? board player)
                                                          (winner-in-diagonals? board player))
                                                    player))))})}
                   [:div "The winner is: " [:span {:x-text "winner"}]]
                   [:div {:style "color: #666; position: absolute; top: 200px, left: 45%;"}
                    [:table
                     (for [i (range 0 3)]
                       [:tr
                        (for [j (range 0 3)]
                          [:td {:id (str i "-" j)
                                :style "border: 1px solid #dedede; margin 1px; height: 50px; width: 50px;
                                        line-height: 50px; text-align: center; background: #fff;"
                                :x-text (->js `(str (aget ~'grid ~i ~j)))
                                :x-on:click (->js `(if (and (= "-" (aget ~'grid ~i ~j))
                                                            (nil? ~'winner))
                                                     (do (aset ~'grid ~i ~j ~'player)
                                                         (set! ~'player (~'otherPlayer ~'player))
                                                         (when-let [w# (~'getWinner ~'grid)]
                                                           (set! ~'winner w#)))))}])])]]]
                  [:script {:type "module"}
                   (h/raw
                    "const squint_core = await import('squint-cljs/src/squint/core.js');
                     const squint_string = await import('squint-cljs/src/squint/string.js');
                     const { default: Alpine } = await import('https://unpkg.com/alpinejs@3.13.3/dist/module.esm.js');
                     Alpine.magic('squint_core', () => squint_core);
                     Alpine.magic('str', () => squint_string);
                     Alpine.start();")]]))
     :status 200}))

(srv/run-server #'page {:port 8888})
(println "Server started at http://localhost:8888")
@(promise)
