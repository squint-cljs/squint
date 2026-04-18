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
## Wontfix

- **#6** `_globalHierarchy` dual-module trap. The playground fix for
  the `_metaSym` analogue unified URLs rather than hoisting state, and
  normal ESM semantics (one instance per realm per URL) is the
  correct behavior — `cljs.core`'s `*global-hierarchy*` is
  module-scoped for the same reason. A user who ends up with two
  instances of `multi.js` under different URLs has a bundler/config
  problem (likely two versions of squint in the graph); sharing state
  across versions via `globalThis` would hide real version-mismatch
  bugs instead of surfacing them. Module-local state stays.

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

## Known perf cliffs (fine for now, document for later)

- Non-primitive `methodTable` / `preferTable` / hierarchy lookups do
  an O(map-size) `findKeyByEquiv` scan on miss. Bounded by relation
  count (typically tiny), not per-call growth. If hotspots emerge,
  canonicalize structural keys via a trie or a stable hash instead of
  linear `_EQ_` scans. Same fix applies uniformly to all three maps.
- Squint `#{…}` / `contains?` are reference-equal for non-primitives,
  so `(contains? (ancestors h [:km :m]) :length)` with a vector in
  the set would miss even though the multimethod runtime now stores
  the relation correctly. Orthogonal to this PR — squint-core
  Set/contains? semantics would need to go through `_EQ_`.
