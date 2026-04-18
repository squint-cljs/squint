# Multimethods PR — follow-ups from review

Captured from PR feedback on the `multimethods` branch.

## Done

- **#1** Cache staleness with 2-arg `derive` — fixed in `f6b36f2`
  (rebuild-and-swap the global hierarchy). Caught a second bug in the
  same commit: `_deriveInto` was walking tag's ancestors instead of its
  descendants, silently corrupting hierarchies.
- **#3** CHANGELOG softened: the zero-cost claim now scopes to "programs
  that use neither multimethods nor `cljs.test`".
- **#4** CHANGELOG now calls out the `cljs.test/report` defn→defmulti
  change as potentially breaking and explains the migration.
- **#5** `defmulti` now auto-wraps a plain `(make-hierarchy)` passed as
  `:hierarchy` so `.deref()` dispatch doesn't crash. Regression test.
- **#6** `_globalHierarchy` moved onto
  `globalThis[Symbol.for('squint.multi.hierarchy')]` so dual module
  loads (e.g. npm + CDN, symlink quirks) share one hierarchy. No
  user-facing test — observable only by reaching into internals.

## Defer (file as follow-up issue)

### 2. `defmulti` re-evaluation wipes installed methods
The compiler emits `(def foo (squint_multi.defmulti …))`, so
re-evaluating `(defmulti foo …)` constructs a fresh `MultiFn` and
drops every previously-installed `defmethod`. Clojure's `defmulti` is
a no-op when the var is already bound, specifically so REPL workflows
preserve installed methods.

A fix would need either:
- A runtime check against a `globalThis`-backed registry so the macro
  expansion reuses an existing `MultiFn` for the same fully-qualified
  name; or
- A compile-time "seen" flag in the ns-state so the second expansion
  skips the rebind.

Friction is acceptable for v1 — squint has similar gaps elsewhere
(`defonce`-style protection is not pervasive). File as a follow-up so
users who hit it have somewhere to thumbs-up.
