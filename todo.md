# Multimethods PR — follow-ups from review

Captured from PR feedback on the `multimethods` branch. Ordered by
urgency: fix-before-merge first, then deferrals and doc notes.

## Fix before merge

### 1. Cache staleness with 2-arg `derive`
`MultiFn.getMethod` invalidates its cache only when
`this.hierarchy.deref() !== this.cachedHierarchy`
(`src/squint/multi.js`). But 2-arg `derive(tag, parent)` calls
`_deriveInto(gh(), …)` which mutates `_globalHierarchy` in place —
identity never changes, so cached methods become stale after a global
derive. Clojure dodges this because `*global-hierarchy*` is rebound
via `alter-var-root` to a new value.

**Fix:** rebuild-and-swap `_globalHierarchy` in the 2-arg path the
same way 3-arg already does (`cloneHierarchy` → `_deriveInto` →
assign). Identity then changes and the cache-invalidation check fires.

### 5. `opts.hierarchy` shape is undocumented and brittle
`defmulti` does `opts.hierarchy || { deref: gh }`. A user passing
`(make-hierarchy)` directly (a plain object, no `.deref`) crashes on
first dispatch.

**Fix:** auto-wrap if not already deref-able:
```js
const hierarchy = opts.hierarchy?.deref
  ? opts.hierarchy
  : { deref: () => opts.hierarchy ?? gh() };
```

### 6. `_globalHierarchy` dual-module trap
Same shape of bug as the `_metaSym` dual-module issue we just fixed in
the playground (`2b0906d`). If `multi.js` ever loads twice under
different URLs (npm dep + ESM CDN, monorepo dedup misses, etc.), each
instance has its own `_globalHierarchy`. A `derive` through one isn't
visible from the other — silent breakage.

**Fix:** hoist to a symbol-keyed global so all instances share it:
```js
const GH = Symbol.for('squint.multi.hierarchy');
function gh() {
  return globalThis[GH] ??= make_hierarchy();
}
```
(Same treatment for any other module-level mutable state here.)

### 3. Soften "zero bundle cost" in CHANGELOG
`src/squint/test.js` now unconditionally imports `multi.js`, so anyone
using `cljs.test` transitively pulls the multimethod runtime. The
claim is still true for non-`cljs.test`, non-multimethod users, but
the CHANGELOG should say so explicitly instead of implying that only
direct `defmulti` usage pays the cost.

**Fix:** tweak the CHANGELOG bullet.

### 4. Breaking change for anyone patching `cljs.test/report`
Going from `defn` to `defmulti` silently breaks any downstream
redefinition of `report`. In practice nothing was broken in the wild
because squint didn't support a plain-fn override path anyway (no
mutable module exports), but it's worth an explicit note in the
CHANGELOG so anyone who was patching via `set!` on a var gets a heads
up.

**Fix:** CHANGELOG note. No code change.

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
