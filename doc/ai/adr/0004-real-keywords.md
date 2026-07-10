# ADR 0004: real keywords at runtime (POC)

Status: Proposed

Branch `poc-real-keywords`. Revisits the keywords-are-strings representation
that ADR 0003 builds on. Byte counts are esbuild `--bundle --minify` output.
Benchmarks are node 22, 10M iterations, per-call figures.

## Context

Squint keywords are plain strings: `:foo/bar` is `"foo/bar"`. That makes
`keyword?` meaningless, prints keywords as strings, and cannot distinguish
`:a` from `"a"` in equality, sets, js Maps or case dispatch. This POC
measures what a real keyword type costs and what it breaks.

## Decision: interned Keyword objects, CLJS equality

`Keyword` is a module-local class in core.js holding one `fqn` field.
`toString` and `toJSON` return the fqn without the leading colon, so object
property access, `str`, template interpolation and `JSON.stringify` behave
like the string representation. `(str :a)` stays `"a"`: returning `":a"`
would break property keys and templates, the main JS interop surfaces.
`pr-str` prints `:a`. Protocol slots supply behavior: IEquiv (keyword-only
equality), IHash (hashes like the name string), IPrintWithWriter.

Interning uses `Map<string, WeakRef<Keyword>>` plus a `FinalizationRegistry`
that prunes dead entries, like Clojure's keyword table. Identity is only
needed among live instances: a collected keyword cannot sit in any live
collection or switch dispatch, so re-interning as a fresh instance is safe.

Equality is strict, like CLJS: a keyword equals only another keyword.
`(= :a "a")` is false. `(keyword? "foo")` is false. `case` follows `=`.
Sets and js Maps follow `=` exactly. An earlier draft made Set/Map lookups
accept both representations, rejected for non-local behavior:
`(get (js/Map. [[:a 1]]) "a")` returned 1 until an unrelated `["a" 2]`
entry was added, then returned 2.

A `Keyword extends String` variant was tried to keep string-method interop
working (`(.toUpperCase :div)`). Rejected: inherited String behavior makes
keywords char-iterable, so walk/seq recurse into them (4 new suite
failures), and libraries still break at their `string?` checks.

## Decision: object maps store names, read back keywords

Object maps are the one place the representations meet. `{:a 1}` and
`{"a" 1}` are the same JS object, so the key representation is lossy by
construction. The POC stores names (string property keys, unchanged) and
reads keywords back: `keys`, `seq`, entries, `reduce-kv` and record
seq/kv-reduce yield keyword keys, like CLJS. The edn reader yields
keywords. Lookups accept both forms on object maps only: `(get m :a)`
indexes by the fqn, `(get m "a")` hits the same entry.

Measured both directions of the lossy representation. With string
read-back and strict `=`, replicant failed 16 assertions (`:click`
literals compared against string keys) and six suite groups failed the
same way. With keyword read-back, replicant fails 2 (deliberately
string-keyed CSS maps like `{"--bg" "red"}`) and babashka/cli fails 3 plus
1 error (command tables keyed by command-name strings). Keyword read-back
wins on numbers and matches CLJS idiom. Maps used as string-keyed
dictionaries need a js/Map (exact keys) or upstream adaptation.

## Compiler

A keyword literal compiles to one module-level
`const _kw_1 = squint_core.keyword("a");` hoisted after the imports, so a
literal in a hot path is a const reference, not a call. The REPL keeps
inline `keyword("a")` calls since top-level consts do not survive re-eval.
Emissions that must stay strings still do: object map literal keys,
destructuring, the object fast paths (`get-inline` and `assoc-inline` emit
`m["a"]`), JSX and html attributes, import attributes.

Keyword literals are tagged `'keyword`, which counts as primitive for `=`:
interning makes `===` a correct equality on keywords, so `(= x :a)` emits
`x === _kw_1`. The pregenerated runtimes were recompiled (test.js,
walk.js) and multi.js's default dispatch value is `(keyword "default")`.
`typeConst` brands keywords as scalars: `assoc`, `conj` and `seq` on a
keyword throw.

## Compatibility

Squint suite: 470 tests, 2504 assertions, 40 failures, none silent
user-code breakage. About 29 assert the old semantics directly (CLJS-side
`=` against strings, emission strings). Four are printing changes
(`pr-str` prints `:a`). Four are js/Map representation changes
(`select-keys` and `merge` onto a js/Map now produce keyword keys). Two
are the remaining tree-shaking floors below. One is `(first :abd)` now
throwing, CLJS parity.

Libtests (reagami added to the set in this branch):

- eucalypt 112, clojure-mode 164: pass
- replicant 254 of 256: the two string-keyed CSS cases above
- babashka/cli: 3 failures, 1 error, string command tables, adapt upstream
- reagami: failed wholesale at first, it called string methods straight
  on hiccup tags (`(.toUpperCase tag)`). Adapted on reagami branch
  `real-keywords`: unconditional `(name tag)` (deleting the squint-only
  reader conditionals), plain string constants for the internal vnode
  property keys, and two test-side normalizations. 70 of 70 assertions
  pass, and the adapted code is also green on stock squint 0.14.203.

## JS interop

Calling squint-compiled functions from JS, and passing JS data in. All
verified on the branch.

JS data into squint. Property-style access is unchanged: `(:type m)` and
destructuring on a JS object work, `(get m :type)` indexes by name. What
breaks is comparison: a squint fn doing `(= (:type m) :click)` or a `case`
on it gets the JS string `"click"` and misses, where the old
representation matched. Squint code consumed from JS must normalize at the
boundary (`(keyword (:type m))`) or compare with strings.

Squint data out to JS. A keyword value reaching JS is an object, not a
string: `v === "click"` and `switch (v) { case "click": ... }` miss.
`String(v)`, template interpolation and `JSON.stringify` all produce the
name (toString/toJSON), so string sinks (DOM attributes, URLs, logs) keep
working. Frameworks that type-check children reject plain objects (React
throws on objects as children), where the old representation rendered.
`(keys m)` handed to JS is an array of Keyword objects: fine as property
keys (`obj[k]` coerces), wrong for `k === "a"`.

`structuredClone` (postMessage, workers, IndexedDB) is the sharpest edge:
it clones the instance as a plain `{fqn: "click"}` object, losing the
prototype and interning. The clone is not a keyword, does not equal one,
and stringifies as `{"fqn":"click"}`. The old representation cloned
losslessly. Data crossing a clone boundary must be lowered first.

The canonical boundary tool is `clj->js`, which now lowers keywords to
their name strings recursively, like CLJS. `js->clj` is unchanged: strings
stay strings, and map read-back keywordizes keys on the squint side.

## Performance

Torture loop, 1M iterations of map literal + `get` + `=` + `case` + `keys`
per iteration, main vs POC: 30ms vs 115ms. Before literal hoisting it was
181ms. The remaining gap is `keys` interning two keywords per iteration,
not a typical profile.

Per-call: hoisted `kw === kw` 1.1ns (plain `===`), `_EQ_` kw/kw 22ns,
dynamic `(keyword s)` intern hit 17ns, `get(m, kw)` on an object 2.6ns via
the fqn index against 1.7ns with a string key. Inlined `m["a"]` paths
unchanged. No `=` deopt remains: keyword literals keep the `===` path via
the `'keyword` tag.

Reagami's own render benchmark, adapted code, stock squint 0.14.203 vs
POC: ~253ms vs ~255ms per trial, equal within noise. Real keywords cost
nothing on a DOM-heavy path. The adaptation itself sped reagami up (~283ms
before, keyword property keys became string constants). Still pending: a
js-framework-benchmark run (https://github.com/borkdude/js-framework-benchmark),
main vs branch.

## Tree-shaking

Two fixes. The class and its protocol wiring sit in a `/* @__PURE__ */`
IIFE and the interning table carries the same annotation, so the
definition is side-effect free. Generic fns (typeConst, `__toFn`, get,
compare, name, namespace) test keywords by a `TYPE_TAG` brand integer on
the prototype instead of `instanceof`, so they do not reference the class.

| imports | main | POC | delta |
|---|---|---|---|
| identity | 39B | 39B | 0 |
| atom, deref, reset!, swap! | 3177B | 3262B | +85B |
| atom, get, assoc, str, keyword | 3856B | 5188B | +1332B |
| conj | 2842B | 4170B | +1328B |

The remaining ~1.3KB applies where keyword construction is semantically
reachable: importing `keyword` itself, or anything that seqs an object map
(read-back must construct keywords). That floor is the read-back feature,
not an accident.

## Open questions

- Upstream adaptations: babashka/cli (js/Map or `name` at boundaries),
  reagami (`name` on hiccup tags).
- `#js/Map {:a 1}` literals hold keyword keys: correct under the model,
  but raw `.get "a"` interop misses. Callers own the key type.
- Whether `keys` read-back allocation matters in real profiles. The
  js-framework-benchmark run above should answer this.
- The two suite DCE caps (get, conj) sit above their old caps because of
  the read-back floor: caps need updating if this lands.
