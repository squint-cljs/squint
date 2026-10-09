# Source maps API

Use `squint.compiler.source-map` to build a source map for JavaScript you
compile in memory, such as a bundle served by a web server. For `squint
compile`, `squint watch` and the vite plugin, see the README.

## Compile with a map

Pass `:source-map` to `squint.compiler/compile*`:

```clojure
(require '[squint.compiler :as squint])

(squint/compile* "(defn f [x]\n  (inc x))" {:source-map true})
```

The result has two extra keys:

- `:source-map-json`: a source map v3 JSON string for `:javascript`.
- `:source-map-segments`: the mappings as a vector of segments.

`:source-map` is `true` or a map with these keys:

- `:file`: the name of the generated file.
- `:source`: the path of the source, or `""` if absent.
- `:source-content`: the text of the source, or the compiled string if absent.

## Segments

A segment is `[gen-line gen-col src-line src-col]`, 0-based. An optional fifth
element is an index into `:sources`, or 0 if absent. Keep segments sorted by
generated position.

## Encode

Use `encode` to turn segments into a v3 JSON string:

```clojure
(require '[squint.compiler.source-map :as sm])

(sm/encode [[0 0 0 0] [1 4 2 3 1]]
           {:file "bundle.js"
            :sources ["src/a.cljs" "src/b.cljs"]
            :sources-content [nil "(ns b)"]})
```

Write `nil` in `:sources-content` for a source without text.

Use `shift-segments` to move segments past the text that precedes their
JavaScript:

```clojure
(sm/shift-segments [[0 0 0 0]] "var x = 1;\n")
;;=> [[1 0 0 0]]
```

## Bundle

Concatenate compiled units into one file with one map:

```clojure
(require '[squint.compiler :as squint]
         '[squint.compiler.source-map :as sm])

(defn bundle [files]
  (loop [i 0 js "" segments []]
    (if (< i (count files))
      (let [{:keys [javascript source-map-segments]}
            (squint/compile* (slurp (nth files i))
                             {:source-map true :elide-imports true :elide-exports true})
            segs (sm/shift-segments (mapv #(conj % i) source-map-segments) js)]
        (recur (inc i) (str js javascript) (into segments segs)))
      {:js (str js "//# sourceMappingURL=bundle.js.map\n")
       :map (sm/encode segments {:file "bundle.js"
                                 :sources files
                                 :sources-content (mapv slurp files)})})))

(let [{:keys [js map]} (bundle ["src/a.cljs" "src/b.cljs"])]
  (spit "bundle.js" js)
  (spit "bundle.js.map" map))
```

Serve `bundle.js.map` next to `bundle.js`.
