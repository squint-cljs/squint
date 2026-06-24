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
