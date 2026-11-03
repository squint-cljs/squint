(require '["https://esm.sh/edn-data@1.1.1/dist/index.js" :as edn])

(edn/parseEDNString "{:a 1 :b #{:foo :bar :baz} :c #inst \"2000\"}"
  {:mapAs :object :keywordAs :string :setAs :set})

(edn/parseEDNString (edn/toEDNStringFromSimpleObject {:a 1 :b 2 :c #{:a :b :c}})
  {:mapAs :object :keywordAs :string :setAs :set})
