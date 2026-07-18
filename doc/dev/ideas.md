# Ideas

Small compiler ideas, not yet done.

## Tag core predicate fns as boolean-returning

The compiler omits the `squint_core.truth_(...)` wrapper around an `if`/`when`
test when it knows the test is already a boolean: inlined ops like `=`, `<`,
`pos?` compile to `_EQ_(...)` / `(x > 0)` with no wrapper. But a call to a core
predicate fn such as `keyword?`, `string?`, `empty?`, `coll?` is not tagged, so
the compiler still wraps it: `if (squint_core.truth_(squint_core.keyword_QMARK_(x)))`.

Idea: mark the core `*?` predicate fns as boolean-returning (a tag the compiler
reads) so `truth_` is elided when one is the test. Removes the wrapper call on
hot predicate checks.

Payoff is runtime/cleanliness, not size: measured on the replicant build, full
`truth_` elision is ~0.2% of gzip (esbuild renames/inlines and gzip crushes the
repeated wrapper token). Pursue for the perf and cleaner output, not bytes.

## `next`/`rest` on arrays via an IndexedSeq view

`next`/`rest` on a JS array do `arr.slice(1)` - an O(n) copy per step. So an
explicit `(loop [s coll] (recur (next s)))` over an array is O(n^2), and `apply`'s
fixed-arg extraction does up to `maxfa` O(n) slices.

Idea (CLJS `IndexedSeq`): `next` on an array returns a small seq view holding
`(array, offset)` instead of slicing. `first` = `array[offset]`, `next` bumps the
offset - O(1), no copy. Fixes the array-`next` cost generally, not just `apply`.

Tradeoffs: adds a small seq class back (size, against the native-arrays/no-seqs
stance); `next` would return an `IndexedSeq`, not a plain array - audit callers
that assume `next`'s result is an array. Option A: general `next` behavior.
Option B: scope narrowly (e.g. `apply` uses an index/offset internally) to avoid
changing `next`'s public result type.

Smaller non-breaking subset: an `apply` array fast-path - index the fixed args +
a single `slice(maxfa)` for the rest, keep `first`/`next` only for lazy seqs. See
doc/ai/adr/0001 for the variadic/multi-arity codegen + lazy apply this builds on.

## Emit direct fixed-arity calls instead of the spread facade

Like CLJS, when a call site passes a statically-known fixed number of args, emit
a direct call to the function's fixed-arity implementation instead of routing
through the variadic/spread facade. A variadic core fn like `conj`/`conj!`
(`function (...xs)`) collects a rest array and spreads on every call, even for
the common `(conj coll x)` 2-arg case; the call site already knows the arity, so
the facade is pure overhead (allocation + dispatch).

Idea: at a fixed-arity call site, target the specific arity directly. The
runtime conj! 2-arg fast-path patch handles one fn; this is the general
compiler-level fix across all variadic/multi-arity calls. See doc/ai/adr/0001 for
the variadic/multi-arity codegen this builds on.

Evidence from the 0.14.199 `min` regression (reagami js-framework-benchmark
remove, +18%): a keyed vdom patch calls `min` once per element, ~7000x per
render. Dispatching on `arguments.length` inside the rest-args fn does not
help: the check itself is free, but V8 only elides the rest array when
inlining plus escape analysis succeed, and in throttled Chrome they did not
(fast-arity runtime min measured as slow as plain rest-args; only the
`Math.min` intrinsic restored 0.14.197 performance). So the fix has to be at
the call site: a per-arity function (CLJS `.cljs$core$IFn$_invoke$arity$2`
style) or inline emission. #885 tried inline emission and #887 reverted it
(double-evaluation); per-arity functions avoid that. Costs bundle size, so
scope to hot core fns first: `min`, `max`, `=`, `conj`, `get`, `assoc`.

## Registry symbols for type brands (duplicated-runtime interop)

Squint brands its types with module-local symbols: `TYPE_TAG` in core.js
marks lists, lazy seqs, sorted collections, and the protocol slots
(`IEquiv__equiv`, ...) are module-local too. Two separately evaluated copies
of core.js (a dependency that bundles or vendors squint) therefore produce
values that do not recognize each other: a list from copy A is an opaque
object to copy B, `=` and the predicates fail across the boundary.

Idea: create the brand and protocol symbols with `Symbol.for` (the global
symbol registry) so branding survives runtime duplication. Cross-copy
`instanceof` still fails, but everything brand-dispatched (typeConst,
protocol lookups) starts working. Interning tables stay per-copy, so
cross-copy identity would rely on equiv, not `===`.

Not a concern for apps with one core.js (the normal case). Surfaced by the
keywords-global review (doc/ai/adr/0004); the hazard predates keywords and
applies to every branded type. Also a prerequisite for the interned keyword
type in doc/ai/adr/0005.
