(ns squint.nrepl-test
  (:require [clojure.test :refer [deftest testing is async]]
            [squint.repl.nrepl-server :as nrepl]
            [squint.repl.nrepl.bencode :as bencode]
            ["net" :as net]))

(defn connect-client [port]
  (let [socket (new net/Socket)]
    (.connect socket port "localhost")
    socket))

(defn send-message [socket msg]
  (.write socket (bencode/encode msg)))

(defn read-response [^js socket cb]
  (let [responses (atom [])]
    (.on socket "data"
         (fn [data]
           (let [[decoded _rest] (bencode/decode-all data :keywordize-keys true)]
             (swap! responses concat decoded)
             (when (some #(contains? (set (:status %)) "done") @responses)
               (cb @responses)))))))

(deftest test-multiple-forms
  (testing "evaluating multiple forms in one message"
    (async done
           (let [port 0 ; random available port
                 server (nrepl/start-server {:port port})
                 session-id (str (random-uuid))]
             (.on
              server "listening"
              (fn []
                (let [address (-> server .address)
                      port (.-port address)
                      socket (connect-client port)]

                   ;; Test case 1: Multiple def forms
                  (testing "multiple def forms"
                    (send-message
                     socket
                     {:op "eval"
                      :code "(def x 1)\n(def y 2)\n(+ x y)"
                      :session session-id
                      :id "1"})
                    (read-response
                     socket
                     (fn [responses]
                       (is (= 3 (count (filter :value responses))))
                       (is (= ["#'user/x" "#'user/y" "3"]
                              (->> responses
                                   (filter :value)
                                   (mapv :value))))

                       ;; Test case 2: Nested forms
                       (send-message
                        socket
                        {:op "eval"
                         :code "(let [a 10\n b 20]\n (+ a b))"
                         :session session-id
                         :id "2"})
                       (read-response
                        socket
                        (fn [responses]
                          (is (= "30" (:value (last responses))))

                          ;; Test case 3: Forms with side effects
                          (send-message
                           socket
                           {:op "eval"
                            :code "(def nums (atom []))\n(swap! nums conj 1)\n(swap! nums conj 2)\n@nums"
                            :session session-id
                            :id "3"})
                          (read-response
                           socket
                           (fn [responses]
                             (is (= "[1 2]" (:value (last responses))))

                             ;; Cleanup
                             (.close server)
                             (done))))))))))))))

  (deftest test-error-handling
    (testing "handling errors in multiple forms"
      (async done
             (let [port 0
                   server (nrepl/start-server {:port port})
                   session-id (str (random-uuid))]
               (.on server "listening"
                    (fn []
                      (let [address (-> server .address)
                            port (.-port address)
                            socket (connect-client port)]

                        ;; Test case: Error in middle form
                        (send-message
                         socket
                         {:op "eval"
                          :code "(def a 1)\n(+ b 2)\n(def c 3)"
                          :session session-id
                          :id "1"})
                        (read-response
                         socket
                         (fn [responses]
                           (is (= "#'user/a" (:value (first responses))))
                           (is (some :ex responses) "Should contain error response")
                           (is (not (some #(= "#'user/c" (:value %)) responses))
                               "Should not evaluate forms after error")

                           ;; Cleanup
                           (.close server)
                           (done))))))))))

  (deftest test-state-preservation
    (testing "preserving state between multiple form evaluations"
      (async done
             (let [port 0
                   server (nrepl/start-server {:port port})
                   session-id (str (random-uuid))]
               (.on server "listening"
                    (fn []
                      (let [address (-> server .address)
                            port (.-port address)
                            socket (connect-client port)]

                        ;; Test case: State preservation
                        (send-message
                         socket
                         {:op "eval"
                          :code "(def state (atom {}))\n(swap! state assoc :a 1)\n(swap! state assoc :b 2)\n@state"
                          :session session-id
                          :id "1"})
                        (read-response
                         socket
                         (fn [responses]
                           (is (= "{:a 1, :b 2}" (:value (last responses))))

                           ;; Verify state in new evaluation
                           (send-message socket
                                         {:op "eval"
                                          :code "(get @state :a)"
                                          :session session-id
                                          :id "2"})
                           (read-response socket
                                          (fn [responses]
                                            (is (= "1" (:value (last responses))))

                                            ;; Cleanup
                                            (.close server)
                                            (done)))))))))))))
