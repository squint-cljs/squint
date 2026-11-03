(require '["https://esm.sh/mutative" :as mut])

(def base-state {:foo :bar
                 :list [{:text :coding}]})

(def state
  (mut/create base-state
    (fn [draft]
      (update! draft :list conj! {:text :learning}))))

state
