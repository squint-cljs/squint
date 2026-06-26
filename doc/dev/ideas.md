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
doc/adr/0001 for the variadic/multi-arity codegen + lazy apply this builds on.
