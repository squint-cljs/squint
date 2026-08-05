# ADR 0008: Kwargs destructuring converts seqs at runtime, rest args by binding form

Status: Accepted

## Context

Clojure destructures a seq as a map by treating it as key/value pairs, so
`(defn f [& {:keys [a]}] a)` and `(let [{:keys [a]} '(:a 1)] a)` both see a map.
`clojure.core/destructure` does this with one runtime test, in front of every
map destructuring:

```clojure
(if (seq? gmap) (seq-to-map-for-destructuring gmap) gmap)
```

squint had neither the conversion nor the support fn, so every kwarg bound to
nil (issue #975).

Two things make Clojure's rule not portable as written:

- squint's `seq?` is iterator-based (`x[Symbol.iterator]`), so it is true for a
  vector, a `js/Map`, a `Set`, a string, and **any iterable JS class instance**.
  Clojure's is an `ISeq` test, true for none of them. `seq?` is not a usable
  guard: it converts a vector Clojure leaves alone, breaks
  `(let [{:keys [a]} (js/Map. ...)] a)`, and slurps an iterable host object into
  a map. The last one is not theoretical - a `seq?`/`associative?` guard OOMed
  the clojure-mode lib test, which destructures CodeMirror objects.
- Variadic rest args are a native JS array (ADR 0001), which is the same runtime
  value as a vector. So a rest arg carries no runtime evidence that it stands
  for kwargs.

## Decision

Two mechanisms, because squint has two situations where Clojure has one.

**A runtime guard on every map destructuring**, for values that are seqs:

```clojure
(if (sequential? gmap)
  (if (vector? gmap) gmap (seq-to-map-for-destructuring gmap))
  gmap)
```

`sequential?` minus `vector?` is squint's `ISeq` test. `sequential?` is
array-or-lazy-or-`IVector`, so it admits lists, lazy seqs and cons cells while
rejecting `js/Map`, `Set`, strings and host objects; `vector?` then removes the
plain arrays. This covers anything reaching the destructuring at runtime,
including through a local or a parameter the compiler cannot see into.

**A compile-time marker on the binding form after `&`**, for rest args.
`mark-rest-args` tags an associative binding form in rest position, and `pmap`
then converts unconditionally (nil-guarded, so no args stays nil as in Clojure).
The marker sits on the binding form, not the call site, so it covers every path
that reaches it: direct calls, `apply`, and vector destructuring.

`seq-to-map-for-destructuring` is added to core with the `cljs.core` name and
contract, and squint's representation (a plain object, not a
`PersistentArrayMap`).

## Consequences

- Fixed: `(f 1 :b 2)`, the trailing map `(f 1 {:b 2})`, the mixed
  `(f :a 1 {:b 2})`, `apply`, multi-arity, `& {:keys [...]}` in vector
  destructuring, and `(let [{:keys [a]} '(:a 1 :b 2)] a)`.
- Not fixed: a rest arg that reaches a map destructuring through a local, e.g.
  `(defn f [& args] (let [{:keys [a]} args] a))`. Clojure gives 1, squint gives
  nil. `args` there is a plain array, indistinguishable from the vector in
  `(let [{:keys [a]} [:a 1]] a)`, which must stay nil. The two cannot both be
  right while rest args are arrays.
- Cost is confined to map destructuring: two predicate calls, measured at
  ~1 ns per site. Variadic calls, fixed-arity calls and every other path are
  untouched.
- The marker is compile-time only, so it does not generalize. That is
  deliberate: the runtime guard is what handles dynamic values, and the marker
  only supplies the one fact no runtime test can recover.

## Alternatives considered

- `seq?` alone, as in Clojure: converts vectors, and breaks `js/Map`
  destructuring. Rejected.
- `seq?` + `associative?`: rules out vectors and `js/Map`, but not an iterable
  class instance, which `seq?` still admits. OOMed the clojure-mode lib test by
  realizing a CodeMirror object into a map. Rejected.
- `sequential?` alone: also matches plain arrays, so a vector is wrongly
  converted. Rejected.
- Rest args as a seq, so one runtime rule covers everything. Tried three ways:
  tagging the array in place as a list (`apply` hands over the caller's array,
  so tagging it mutates a value the caller still holds), `(concat rest)`, and an
  `array-seq` view over the array. All of them work and give full parity, but
  they put an allocation on every variadic call and cost core's array fast paths
  in whatever the body does with the rest arg. Measured against a native rest
  array: `(fn [& xs] (count xs))` 12.8 -> 40.4 ms per 2M calls, `str/join` over
  the rest 3x, `(fn [& xs] 1)` 1.1 -> 16.5 ms. `count`/`nth` could be given back
  via an `ICounted`/`IIndexed` `ArraySeq`, but the class pins `LazyIterable` into
  every bundle and breaks the DCE floor test (1016B against an 800B cap).
  Rejected: every variadic fn in every program pays, to fix one destructuring
  shape.
- Erroring on a rest arg destructured through a local: no way to detect it.
