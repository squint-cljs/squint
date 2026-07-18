# ADR 0006: Core fns fast-path native JS data before protocol dispatch

Status: Accepted

## Context

squint's data model is native: a map is a plain `Object`, a vector is an
`Array`, a keyword or string is a JS string, a number is a JS number. Custom
types (records, deftypes, sorted collections) exist but are the exception, not
the common case.

Core fns must also serve those exotic types. Since 0.14.203 that means protocol
slots (`IEquiv`, `ILookup`, `IAssociative`, ...) and cross-representation
handling (a plain object vs a `js/Map`, a sorted map vs any map). Recognizing
them costs type guards: symbol-keyed property probes (`x[IEquiv__equiv]`) and
helper calls (`isSortedMap`, `isMapLike`, `isSetLike`).

When a fn runs those guards first, every call pays for them, including the
overwhelmingly common plain-object, array and primitive case. On a primitive it
is worse: each guard reaches into the value for a property, and a property read
on a primitive boxes it into a wrapper for a lookup that always misses.

This regressed real code. In 0.14.203 `dequal` grew a preamble of two `IEquiv`
probes plus `isSortedMap`, `isMapLike` and `isSetLike` calls ahead of the plain
paths, so `=` on plain objects, arrays and primitives slowed 6-22% (measured
with a separate-process interleaved microbench). `assoc` gained an unconditional
`IAssociative` slot check.

## Decision

In core fns the native cases come first. Check plain `Object` / `Array` /
primitive with cheap `typeof` and `constructor` tests and return down the fast
path before any protocol slot probe, sorted/maplike/setlike guard, or
exotic-type helper call. Protocol and exotic-type dispatch runs only after the
native cases are ruled out, reached by values that are neither a plain object,
an array, nor a primitive.

Primitives bail before any property access, since a property read on a
primitive boxes it:

```js
function dequal(foo, bar) {
  if (foo === bar) return true;
  if (foo == null) return bar == null;
  if (bar == null) return false;
  if (typeof foo !== 'object' || typeof bar !== 'object') return false; // primitives
  var ctor = foo.constructor;
  if (ctor === bar.constructor && (ctor === Object || ctor === Array)) {
    return dequalSameCtor(foo, bar, ctor);                              // plain data
  }
  // only now: IEquiv probes, isSortedMap, isMapLike, isSetLike, cross-type ...
}
```

`get` and `conj` already worked this way (`isObj`/`typeConst` first, slot check
only for instance types). This ADR makes it the rule and extends it to `dequal`,
`equiv` and `assoc`.

## Consequences

- `=` on plain data 6-22% faster, `assoc` skips the slot lookup for plain
  objects and arrays, primitive `=` does no boxing. Exotic types are unchanged
  bar one ruled-out branch, which is negligible.
- The guard order is load-bearing and easy to regress: a feature that adds a
  check at the top of a core fn re-taxes every plain-data call, silently. `bb
  test:size` guards the bundle side; a separate-process interleaved microbench
  is the way to catch the speed side (a single-process A/B is unreliable, see
  doc/dev/dce.md and the benchmarking traps in ADR 0004).
- The reorder must preserve semantics. The fast path fires only when the value
  provably cannot carry a protocol slot or a brand (a plain `Object` or `Array`
  constructor), so a custom type that extends a protocol still reaches its slot.
- `=` still pays the variadic calling convention (rest array + `walkArray`) on
  top of the now-cheap body. Removing that, and the analogous per-call cost on
  `min`/`max`, is the fixed-arity call emission in doc/ai/ideas.md.

## Alternatives considered

- Guards first, no fast path (the state after 0.14.203): simplest, but taxes the
  common case on every call. Rejected on the measured regression.
- A single `typeConst` switch at the top of every fn: uniform, but `typeConst`
  itself runs `instanceof Map`/`Set` and a brand probe; for the plainest case a
  direct `isObj`/`constructor` check is cheaper. Used where a fn already needs
  the full type (`assoc!`, `copy`), not forced everywhere.
- Micro-tuning the guards (memoize `isMapLike`, etc.): treats the symptom, the
  values still get probed. Reordering removes the probes entirely for plain
  data.
