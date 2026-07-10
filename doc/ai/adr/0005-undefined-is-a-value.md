# ADR 0005: undefined is a value, not-found is the default argument

Status: Accepted, branch `immutable` (July 2026)

## Context

JS has two nils. Squint compiles `nil` to `null` and `nil?` accepts both
`null` and `undefined`. Historically the runtime `get` folded a stored
`undefined` value into not-found: `(get m k default)` returned `default`
for a key that was present with an `undefined` value. `find` tested the
result of 2-arg `get` against `undefined`, so such entries were invisible
to `find` while `contains?` (which uses `in`/`has`) reported them present.
Users had already bumped into the resulting get undefined/nil confusion.

Two designs were considered:

1. "undefined is the missing value" throughout: align `contains?`, `find`
   and set membership with get's fold, matching the JSON/optional-props JS
   convention. Rejected: JS Sets and Maps store `undefined` as a
   first-class member (`new Set([1, undefined]).has(undefined)` is true),
   so folding makes squint deny membership the platform affirms, and
   `count`/`seq` would still see the entries. The convention cannot be
   made coherent without taxing every traversal.
2. Presence semantics: an entry is present when the key is present,
   whatever the value. Chosen.

## Decision

Key presence decides, values never do. A default argument only applies
when the key is absent. When nothing is found and no default is given,
core functions return nil (either JS nil is fine).

- `get` on plain objects, arrays and js Maps checks `in`/`has` when the
  looked-up value is `undefined`, on the hit path nothing changes. Sets
  return the key on `has`. The `ILookup` branch now trusts the protocol
  impl to return the not-found argument instead of re-folding `undefined`.
- `find` looks up with a module-local sentinel as the default, so entries
  holding `undefined` are found. No second lookup.
- `contains?` was already presence-based (`in`, `has`, contains-key slot)
  and is unchanged.
- `recordLookup` checks `hasOwnProperty` when the field value is
  `undefined`.
- Compiler-inlined lookups already agreed: 3-arg `get-inline` emits
  `(k in obj ? obj[k] : nf)` and `:or` destructuring emits the same
  pattern. The runtime was the only outlier.

This matches CLJS: `(get {:a nil} :a :nf)` returns `nil`, and
`(find {:a nil} :a)` returns `[:a nil]`. In squint the same holds for
values that are `undefined`.

Not changed: 2-arg lookups that miss still return whatever JS produces
(`undefined` from property access, `null` from e.g. fetch Headers). Both
are nil. The duck-typed `.get` interop branch in `get` keeps best-effort
semantics.

## Consequences

- `(get m k default)` can now return `undefined` (an entry that stores
  it). Code that used the default to paper over stored `undefined` sees a
  behavior change.
- `find`, `select-keys`, `merge-with` and other presence-driven fns agree
  with `contains?` for every value.
- `squint.immutable` lookups (ADR 0004) respect the not-found argument
  through the trusted `ILookup` branch, no HAMT changes were needed.
