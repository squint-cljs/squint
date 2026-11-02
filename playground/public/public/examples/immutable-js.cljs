(require '["https://esm.sh/immutable" :as imm])

(let [m (new imm/Map [[1 2]])
      m (imm/set m :foo :bar)]
  (:foo (.toJSON m)))
