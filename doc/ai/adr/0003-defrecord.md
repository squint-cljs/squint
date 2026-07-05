# ADR 0003: defrecord

Status: Accepted

How squint implements `defrecord` and why. Decisions made across the
protocol work of squint 0.14.201 through 0.14.203.

All byte counts are esbuild `--bundle --minify` output, raw and gzipped.

## Context

Squint data is plain JS: maps are objects, keywords are strings, vectors are
arrays. CLJS code migrating to squint uses `defrecord` heavily, and reactive
libraries (reagent-style atoms, cursors) need typed values that still behave
as maps. Before this work squint had no records at all.

The prerequisite was the protocol layer: `ILookup`, `IAssociative`, `IMap`,
`ICounted`, `IKVReduce`, `ICollection`, `IEmptyableCollection` and `IEquiv`,
dispatched through per-method Symbol slots. The dispatch lives only on the
`INSTANCE_TYPE` extension path of `typeConst` (class instances and
null-prototype objects), so plain objects and arrays keep their fast paths
untouched. Measured cost on built-in types: none.

## Decision: records are constructors with unmunged string-keyed own properties

`(defrecord Foo [a first-name])` emits a plain constructor function that
assigns `this["a"]` and `this["first-name"]`. Parameter names are munged,
property keys are not.

Why: squint map keys are strings, so `(:first-name rec)`, `keys`, `seq` and
`reduce-kv` must see the exact field name as the key. CLJS munges the
property (`this.first_name = first_name`) and compensates with per-record
generated code: `-lookup` carries a literal `case` mapping each keyword back
to its munged accessor, and `-seq` rebuilds entries with the keyword keys.
That per-type table generation is exactly what the shared-implementation
design (below) removes, so squint stores the map key as the property key and
the shared impls read fields with no translation.

Consequences: fields live as own enumerable props, so the extmap needs no
separate structure and an extra `assoc`'d key is just one more own property.
JS dot interop on dashed fields differs from CLJS: `rec.first_name` works
there, squint needs `rec["first-name"]`. Undashed fields dot-access the same
in both.

## Decision: behavior through the shared protocols, not special cases

A first implementation branded records with a symbol and special-cased them
inside `copy`, `dissoc`, `dequal` and `toEDN`. Rejected: every special case
is a capability only records get. Routing records through the same protocol
slots any type can implement made the machinery general. The immutable.js
integration (`extend-type im/Map ...`) works because of this choice, and the
suite exercises it.

Chasing this surfaced and fixed real dispatch gaps: `into`, `merge`,
`assoc-in`/`update-in`, `keys`/`vals`, `update-keys`/`update-vals` all read
or wrote raw properties instead of dispatching. `merge` additionally cannot
rebuild a record from `(empty rec)` (nil, CLJS parity), so a `-conj` target
skips the defensive copy: `-conj` types are immutable by contract.

## Decision: one shared implementation set in squint-cljs/src/squint/record.js

The nine protocol implementations are identical for every record type, so
they are defined once and each `defrecord` emits a single
`squint_record.attach(Foo.prototype, ["a", "first-name"])` call. The basis
vector is stored as the `IRecord` marker value on the prototype; `-dissoc`
reads it to decide between staying a record and demoting to a plain map.

Numbers against per-record generated implementations:

| records in app | generated | shared |
|---|---|---|
| 1  | 11963 / 4163 gz | 8331 / 3117 gz |
| 10 | 26872 / 5603 gz | 10866 / 3475 gz |

Marginal cost per record type: ~280 B raw / ~40 B gz. Performance is parity
(kv-reduce faster, equality within a few percent), with one structural win:
all record types point a slot at the same function, so slot call sites stay
monomorphic no matter how many record types an app defines. Per-type
generated bodies degrade inline caches as the type count grows.

The module is imported automatically only by files that use `defrecord`,
through the same compiler flag mechanism as `squint_multi`. This was chosen
over a public `attach-record-impls!` core var (pollutes the resolvable core
namespace) and over a `__`-private core export (macro emission resolves
through core.edn and cannot reach those).

## Decision: no shared dispatch functions

The inverse sharing is a trap. Generating the sixteen protocol-method fns
(`_lookup`, `_deref`, ...) from one factory closure was tried and reverted:
closures born at one source position share a V8 feedback vector, so the
single `o[slot]` site inside sees every slot symbol and goes permanently
megamorphic. Measured 1.8 ns to 11 ns per call, even when only one of the
sixteen is ever used. Rule: share implementation targets, never dispatch
code objects. Dedupe of dispatch code belongs at build or macro time.

## Decision: field access in method bodies via a filtered let

CLJS resolves bare field names in method bodies per use site: `deftype*` is
a special form whose analyzer scopes the fields as locals over the method
bodies and emits each use as a `self__.field` read, after macroexpansion.
Squint does the same with its own machinery: `core-defrecord` wraps the
user impls in a `record-methods*` special that merges each field into the
emitter's `:var->ident` map as a `self__["field"]` ident (extend-type
already prefixes every method with `const self__ = this;`). Fn params and
let bindings merge into the same map later, so they shadow fields exactly
like locals shadow anything else.

Bracket idents would be destroyed by the emitter's `munge**`, so the
var->ident hit honors the existing `:squint.compiler/no-rename` metadata
and emits such idents verbatim. This is the one compiler change involved.

Two designs were tried and rejected first: a `let` prefix binding every
field per method (dead reads on every call), and a filtered variant binding
only fields the unexpanded body mentions. The filter is unsound: which
names a body uses is only knowable after macroexpansion, and a macro
expanding to a bare field name defeats any syntactic scan. The var->ident
mapping resolves at emission time, after expansion, so
`(defmacro get-a [] 'a)` used inside a method body reads the field, exactly
as in CLJS.

Emission hygiene notes that came out of review:

- Reader `^boolean` hints are dropped by syntax quote; boolean tags on test
  positions are attached with explicit `with-meta`, which is what removes
  the `truth_` wrappers from the generated code.
- Generated binding names are fixed symbols, not gensyms: the generated
  bodies contain no user code, so capture is impossible and the compiler
  output stays deterministic.
- `prepare-protocol-masks` (CLJS fast-path bitmasks) does nothing in squint
  and is not called.

## Semantics summary (CLJS parity choices)

- `assoc` keeps the record type, on basis and non-basis keys alike.
- `dissoc` of a basis field returns a plain map; of an extra key, a record.
- `(empty rec)` is nil.
- Equality is prototype identity plus own-property comparison; `=` dispatches
  `-equiv` on the left argument only, like CLJS.
- Printing gives `#TypeName{:a 1}`.
- `->Foo` and `map->Foo` are generated; `map->Foo` accepts extra keys.
- `record?` and `(satisfies? IRecord x)` identify records.

## Known divergences

- Field keys are strings, like all squint map keys.
- No `IRecord`/`IEquiv` protocol layering beyond the marker and slots.
- Records of different types with equal fields are unequal (as in CLJS), but
  a record never equals a plain map with the same entries in either
  direction.
