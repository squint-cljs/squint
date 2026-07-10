# ADR 0004: real keywords at runtime (POC)

Status: Proposed

Branch `poc-real-keywords`, commits 36c56ef, 9d5fa49, 4b9d2c8. Revisits the
keywords-are-strings representation that ADR 0003 builds on. Byte counts are
esbuild `--bundle --minify` output. Benchmarks are node 22, 10M iterations,
per-call figures.

## Context

Squint keywords are plain strings: `:foo/bar` is `"foo/bar"`. That makes
`keyword?` meaningless, prints keywords as strings, and cannot distinguish
`:a` from `"a"` in sets, js Maps or case dispatch. This POC measures what a
real keyword type costs and what it breaks, with backward compatibility as
the primary constraint.

## Decision: interned Keyword objects with a string-compatible surface

`Keyword` is a module-local class in core.js holding one `fqn` field.
`toString` and `toJSON` return the fqn without the leading colon, so object
property access, `str`, template interpolation and `JSON.stringify` behave
exactly like the string representation.

Interning uses `Map<string, WeakRef<Keyword>>` plus a `FinalizationRegistry`
that prunes dead entries, like Clojure's keyword table. Identity is only
needed among live instances: a collected keyword cannot sit in any live
collection or switch dispatch, so re-interning as a fresh instance is safe.
Interning makes `===`, native Set/Map membership and switch labels work.

TODO: stale? Behavior plugs into the existing protocol slots. `IEquiv`: a keyword equals
another keyword or its own name string (the compat shim). `IHash`: hashes
like the name string, consistent with equiv. `IPrintWithWriter`: `pr-str`
prints `:foo`.

The compiler emits `squint_core.keyword("foo")` for a keyword literal,
tagged `'keyword`. Everything that must stay a string still emits a string:
object map literal keys, destructuring, the object fast paths (`get-inline`
and `assoc-inline` still emit `m["a"]`), JSX and html attributes, import
attributes. Object maps keep string keys throughout.

`case` on a keyword test emits two labels, `case keyword("x"): case "x":`,
so string data keeps matching keyword branches. The macro-level duplicate
constant check already rejects `:x` and `"x"` in one case form.

`=` emits `===` only when both sides have primitive tags or either side is a
known number or boolean. A known string against an unknown side goes through
`_EQ_` because the unknown side can hold a keyword.

TODO: this feels wrong? `get` and `contains?` on Set and js Map retry a miss with the alternate
representation (string to live interned keyword, keyword to fqn), so
membership follows `=`. Replicant's `(#{:on :innerHTML} k)` over string map
keys depends on this.

`typeConst` treats a keyword as a scalar: `assoc`, `conj` and `seq` on a
keyword throw instead of treating it as an associative instance.

## Compatibility

Squint suite: 470 tests, 2504 assertions, 42 failures, none silent user-code
breakage. Breakdown: about 30 assert the old semantics directly, comparing
squint results to strings with CLJS `=` or asserting emission strings. Five
are printing changes (`pr-str` now prints `:a`, not `"a"`). TODO: CONCERNING Four are the
tree-shaking floor below. Two are js Map literals now holding keyword keys,
so `.get "a"` interop misses. One is `(first :abd)` now throwing, which is
CLJS parity.

Libtests all pass: eucalypt 112, clojure-mode 164, replicant 256,
babashka/cli 472 assertions. Replicant needed the Set/Map alternate-key
lookup, the other three passed unmodified.

Preserved: `(= :a "a")` is true in every position <- TODO: this feels wrong, `(str :a)` is `"a"`,
`(keys {:a 1})` returns strings, `(:a m)` and destructuring on string-keyed
data, `(map :a xs)`, case in both directions, sorting mixed keywords and
strings. Deviations: `(keyword? "foo")` is now false, keywords are not
seqable, js Map literals hold keyword keys.

## Performance

- interned `keyword("hot")` hit: 17ns
- `===`: 1.2ns, `_EQ_` string/string: 22ns, keyword/string: 30ns,
  keyword/keyword: 38ns including the intern call
- `get(m, kw)` on an object: 31ns against 6ns for `get(m, "foo")` and 0.7ns
  for inlined `m["a"]` (property key coercion calls toString per lookup)

Costs: every keyword literal outside an inlined position is a function call
where it used to be a string constant. `=` between a string literal and an
untagged expression dropped from `===` to `_EQ_`. The object fast path is
unaffected since it still emits `m["a"]`. None of this is visible in the
libtest suites but no hot-loop application was measured.

TODO: we need performance tests before and after. 

## Tree-shaking

Bundle deltas, main against POC:

| imports | main | POC | delta |
|---|---|---|---|
| identity | 39B | 1073B | +1034B |
| atom, deref, reset!, swap! | 3177B | 4499B | +1322B |
| atom, get, assoc, str, keyword | 3856B | 5305B | +1449B |
| conj | 2842B | 3903B | +1061B |

Every bundle pays roughly 1.0 to 1.4KB. Two causes. The prototype wiring
(`Keyword.prototype[IEquiv__equiv] = ...`) is top-level side effects, which
retains the class, cache, WeakRef and registry in every bundle including
keyword-free ones. And `typeConst`, `__toFn` and `get` reference `Keyword`
directly, pinning it into anything that touches collections. The first cause
is fixable with a `@__PURE__` wrapper or static block so the wiring shakes
out of keyword-free bundles. The second is a floor for any bundle that uses
collection functions.

## Open questions

- Keep `(= :a "a")` true? CLJS says false. Dropping the shim breaks all code
  that compares `(keys m)` output against keyword literals.
- `(str :a)` stays `"a"` here, CLJS says `":a"`. Changing it breaks property
  access and templates.
- `(keyword? "foo")` false: how much code in the wild feeds strings to
  keyword predicates.
- js Map literal keys: keyword keys are consistent with real keywords but
  break `.get "a"` interop.
- The `_EQ_` deopt for string-literal comparisons on hot paths.
- Recovering the tree-shaking floor for keyword-free bundles.
