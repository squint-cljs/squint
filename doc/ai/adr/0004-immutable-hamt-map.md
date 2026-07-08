# ADR 0004: Optional persistent HAMT map (squint.immutable)

Status: Prototype, branch `immutable`

## Context

Squint maps are plain JS objects: string keys only, assoc copies the whole
object. Composite (value-equal) keys are impossible and large maps pay O(n)
per update. `doc/dev/composite-map-keys.md` explored an encoder-based EncMap;
its option 3 was "a real hashed map". This ADR covers that map: an opt-in
persistent HAMT with value-semantic keys that plugs into core's existing
protocol dispatch, without changing squint's default data representation.

Constraints:

- Core's public export surface stays additive-only and small.
- Apps that do not use the module must pay ~nothing (tree-shaking).
- Key equality must be core `=` (dequal), so hashing must satisfy
  `(= a b)` implies `(hash a) === (hash b)` against dequal's equality
  classes (vector = list = lazy seq, plain object = js/Map by entries).

## Decision

### Module

`src/squint/immutable.js`, required as `(:require [squint.immutable :as i])`.
The ns is registered in `library-imports` (compiler_common.cljc) and
`lib-modules` (bb/tasks.clj, generates lib_vars.edn), like squint.string.
String-path requires keep working. The playground copies the module in
`playground/bb.edn` init and maps it in `playground/public/index.html`.

Exports: `hash-map`, `hash-map?`, `obj-view`, plus re-exports of the hash
API that lives in core.

### Data structure

CLJS PersistentHashMap port: BitmapIndexedNode (spills to a 32-way ArrayNode
past 16 children), ArrayNode (packs back below 8), HashCollisionNode,
nil-key slot on the map object. Persistent arities only; the transient
handle wraps the persistent ops (correct, no node editing yet).

Instances carry no `TYPE_TAG`, so core dispatch routes them through the
INSTANCE_TYPE extension path. The prototype fills the existing slots
(ILookup, IAssociative, IMap, ICounted, IKVReduce, ICollection,
IEmptyableCollection, IEquiv, IEditableCollection, IEncodeJS, IHash, and the
transient slots on the handle), so `get`/`assoc`/`dissoc`/`conj`/`count`/
`seq`/`contains?`/`reduce-kv`/`=`/`into`/`merge`/`merge-with`/`update-in`/
`select-keys` etc. work from core unchanged. Slot methods live behind pure
IIFE wrappers for DCE (techniques in doc/dev/dce.md).

### Persistent vector

`vector`, `vec`, `vector?` (instance predicate) in the same module: CLJS
PersistentVector port (32-way bit-partitioned trie + tail, root overflow
and pop-with-shift-shrink included), persistent arities only, transient
handle wrapping the persistent ops like the map's. Prototype fills
ILookup (number keys), IAssociative (index assoc, append at cnt),
ICounted, IIndexed, ICollection, IEmptyableCollection, IEquiv
(sequential: pvec = array = list = lazy seq), IKVReduce (index/value),
IStack, IHash (cached ordered), IEncodeJS (deep array), IEditableCollection
plus transient slots, IVector marker, iterator and the print hook.

New core protocols for it (same pattern as the map's): `IStack`
(-peek/-pop), `IIndexed` (-nth), `IVector` marker,
`ITransientVector_-pop!`. Core dispatch edits: `nth` checks the IIndexed
slot before the iterable walk, `peek`/`pop`/`pop!` check slots before
throwing, `vector?`/`sequential?`/`vec` recognize the IVector marker, and
`subvec` slices an IVector generically through its slots (returns the same
type). Non-users pay nothing: the core 10-fn mix bundle is byte-identical.

Vector-only import: 9.2KB min / 3.4KB gzip - it tree-shakes independently
of the map. Map+vector: 20.8KB / 6.9KB.

Known edge: `(conj {:a 1} (i/vector :k 5))` on a PLAIN object goes through
core's array-specific entry path and does not treat the pvec as an entry;
hamt-map conj handles pvec entries. Subvec on pvec is O(n) conj, not a
Subvec view type.

### Persistent hash set

`hash-set`, `set` (conversion), `hash-set?` in the same module: a wrapper
over the persistent hash map (elements as keys mapping to themselves), like
CLJS PersistentHashSet. Slots: ILookup (get returns the stored element),
contains? slot without the IAssociative marker (sets are not associative),
ICounted, ICollection, ISet (-disjoin, so core disj works), IEquiv, IHash
(unordered, cached), IEmptyableCollection, IEncodeJS (array),
IEditableCollection + transient handle (conj!/disj!), print hook (#{...}).
Equality iterates the OTHER side and membership-tests on this side: our
contains is value-based where js/Set .has is reference-based, so
`(= (i/hash-set [1 2]) ...)` composite elements work. Core edit: `set?`
recognizes the ISet marker. Set-only import: 17.9KB min / 6.0KB gzip (it
carries the map). All three structures: 22.3KB / 7.2KB gzip. clojure.set
(union/difference/...) still expects js/Set - convert at that boundary.

### clojure.set is polymorphic

set.js dispatches per collection kind: js/Set (and set-likes with
reference .has/.add, e.g. SortedSet) keep the old fast paths and result
types; persistent sets go through core slots (count/contains?/transient
conj!/disj!) and results preserve the persistent type of the larger
(union) or smaller (intersection) argument. Membership tests against a
persistent side are value-based. rename-keys/map-invert previously
corrupted hamt maps via assoc! instance fallthrough; they now use
persistent assoc for slot-maps and keep the mutating fast path for plain
objects. project/rename preserve the set type; join still returns a js
Set (accumulator), documented.

### Metadata via IMeta/IWithMeta protocols

The `_metaSym` property scheme is gone (breaking). `meta`/`with-meta`
dispatch through new core IMeta_-meta / IWithMeta_-with-meta slots, the
only mechanism. The immutable types implement them type-level (a `meta`
field, threaded through every value op like CLJS: assoc/dissoc/conj/pop/
empty keep meta; equality and hashing ignore it; transients drop it).
Plain values (objects, arrays, js Maps/Sets, fns, lazy seqs) get
instance-level impls installed by with-meta: the meta value lives in a
closure under the IMeta slot, copyMeta forwards the slots, so the old
observable behavior (value ops carry meta) is unchanged and the whole
test surface passes. Atoms implement IMeta only (like CLJS), which keeps
the with-meta copy machinery out of atom-only bundles (caught by the dce
floor test).

### Persistent list: deliberately absent

Squint's array-backed List, lazy Cons/seqs, and pvec-as-stack cover the
use cases; CLJS PersistentList would only buy O(1) head-conj sharing.

### Hashing lives in core.js

Murmur3 like CLJS: string hash cache (8192 entries, reset on overflow),
`hash-ordered-coll` for sequential iterables, `hash-unordered-coll` for sets
and map entries, java-style number/boolean cases, `IHash` protocol slot
(`IHash_-hash`, matching defprotocol emission, so plain
`(extend-type T IHash (-hash [x] ...))` works).

It lives in core.js, not the module, because the `(= a b)` implies equal
hash contract couples hash to dequal: one file keeps them in lockstep, and
hash reads the `TYPE_TAG`/`SORTED_TAG` brands directly instead of
duck-typing sorted collections. `(hash x)` is a core var (bump-core-vars).

`=` never calls hash (in CLJS either): compare bails at the first mismatch
and exploits reference sharing, while a hash must consume the whole
structure and still owes the compare on a match. Hash pays off only
amortized: one-vs-many (the HAMT lookup itself) or cached-on-immutable (the
map's own lazy `_hash`, CLJS `caching-hash` style). Key hashes are computed
per operation and not stored, like CLJS. Hashing plain mutable objects can
never be cached (stale after mutation, spread copies enumerable symbol
props, hidden-class pollution), which is why there is no "secret hash on
plain objects".

Divergences from CLJS hashing, both forced by squint's plain-data rep:
plain objects/arrays hash by value (CLJS uses goog/getUid identity, but in
squint they are the collections), and `(hash :foo)` = `(hash "foo")`.

### core.js changes (14 lines + hash section + IEncodeJS)

- `dequal` dispatches `-equiv` on the right argument when only that side has
  it. CLJS is left-only, but in CLJS both sides always carry the protocol
  (`{}` is a PersistentArrayMap); squint's plain object cannot, so left-only
  made `=` asymmetric.
- `seq` materializes distinct tagged entries for an instance with an
  `IMap__dissoc` slot, like the js/Map branch.
- `toEDN` calls a `squint$lang$edn` method when a value has one; hamt maps
  print `{:a 1, :b 2}`.
- `map?` is true for instances marked `IMap.__sym`.
- `IEncodeJS` protocol (like CLJS): `clj->js` checks the slot first and
  passes a cycle-safe recur fn. The hamt impl snapshots to a deep plain
  object, non-string keys via `pr-str` like CLJS key->js.

### JS interop

`obj-view` wraps a map in a live read-only Proxy for JS APIs that expect a
plain object: property access, destructuring, spread, `Object.keys`,
`JSON.stringify`, `in` all work, writes throw. String keys only. Reads cost
~1.5x a hamt get (measured: 1M reads, plain object 13ms, hamt get 23ms,
view 35ms). Squint code keeps using the map itself; making `hash-map`
return a proxy was rejected: every internal slot/field access would pay
traps and protocol impls would read fields through the string trap.

### Per-use immutability hint: the refer pattern

Requirement: opt into hamt maps locally at the use site - never a namespace
or global switch - writing CLJS-style code, portable to .cljs/.cljc on
CLJS/JVM/bb with zero setup. Options considered:

| | verdict |
|---|---|
| ns metadata / squint.edn flag | rejected: flips a whole ns or project |
| `^:squint/immutable {}` metadata | rejected: becomes runtime metadata in CLJS/JVM (verified in planck; survives assoc) |
| `#i {}` unqualified tag | rejected: CLJS does NOT ignore unknown tags (verified in analyzer.cljc: tools.reader + data_readers, no default-data-reader-fn), and unqualified user tags cannot be defined there - squint-only forever |
| `#squint/i {}` qualified tag | rejected: needs a data_readers.cljc shim dep in every consumer |
| compiler-known marker fn / literal-walking macro | workable, but new machinery; kept as a possible sugar layer later |
| refer pattern (chosen) | zero new syntax, zero setup, zero residue |

The chosen pattern:

```clojure
(ns foo
  #?(:squint (:refer-clojure :exclude [hash-map]))
  #?(:squint (:require [squint.immutable :refer [hash-map]])))

(get (hash-map 1 2 3 4) 1)
```

The require IS the hint. Under CLJS/JVM the conditionals vanish and core
hash-map already builds persistent maps: identical semantics on all
platforms. Squint already supported everything needed (:squint reader
feature, library ns registration, :refer).

Making it sound required a compiler fix: return-tag inference
(`fn-return-tags`) marked `(hash-map ...)` calls 'object without checking
whether the symbol still resolves to core, so `(get (hash-map 1 2) 1)`
inlined to property access (`hash_map(1, 2)[1]`) and returned undefined on
a hamt map. Two bugs: the tag guards ignored refers, and `maybe-core-var`
compared the munged name against the raw symbols stored by
`:refer-clojure :exclude`, so excludes never applied there.
`resolves-to-core?` now mirrors symbol-emission precedence (local shadow,
current-ns var, refer, exclude) before any core tag applies. The core fast
path (plain `(get (hash-map :a 1) :a)` -> `["a"]`) is unchanged and
pinned by tests.

Pitfall worth knowing: a bare vector ns clause
(`#?(:squint [squint.immutable ...])`, missing the `(:require ...)`
wrapper) is silently ignored by the ns handler.

The playground compiles with the release pinned in its importmap
("squint-cljs" -> unpkg), so branch features are invisible there until a
release; local dev now loads the compiler from the squint-local symlink
(playground/public/js/main_js.mjs), so `bb dev` serves the working tree.

## Measurements

### Bundle cost (esbuild 0.28, --bundle --minify --format=esm)

| entry                              | min bytes | gzip |
|------------------------------------|-----------|------|
| core `identity` floor              |    16     |   56 |
| core `=` only                      |  2169     |  912 |
| core 10-fn mix                     | 13631     | 4722 |
| core 10-fn mix + hamt hash-map     | 22265     | 7593 |
| hamt hash-map only                 | 15765     | 5593 |
| hash fns only                      |  1739     |  898 |

Marginal cost for an app already on core: ~8.6KB min / ~2.9KB gzip. Apps
not using the module pay +228 bytes for the core edits; `=`-only bundles
contain no murmur constants (verified by grep). The hamt-only figure
includes the pr-str/toEDN chain pulled by the clj->js slot. Gotcha found on
the way: `new Int32Array(f64.buffer)` as a top-level const pins the buffer
into every bundle even under a pure annotation (the `.buffer` property read
could be a getter); both views are built in one pure IIFE.

### Performance (node v22, 200k string keys, 50k vector keys)

plk column is planck 2.28 on JavaScriptCore with reduce-based loops: an
engine difference rides on top of the library difference. Read it as "what
a plk user sees", not a same-engine comparison.

| | squint hamt | Immutable.js 5.1.9 | CLJS (plk) |
|---|---|---|---|
| persistent build | 102 ms | 52 ms | 917 ms |
| batch/transient build | 87 ms (path-copying) | 23 ms (withMutations) | 506 ms |
| get hit | 33 ms | 15 ms | 367 ms |
| get miss | 55 ms | 10 ms | 335 ms |
| delete all | 76 ms | 56 ms | 844 ms |
| composite key assoc+get | 27 ms (raw vectors) | 56 ms (I.List wrap) | 264 ms |

Immutable's remaining edge is real transient node editing and string-hash
tuning. The composite-key case, the module's reason to exist, wins outright.

## Alternatives and prior art

### Immutable.js

Same 32-way HAMT, wrong semantics for squint. Does not tree-shake (Map-only
import = 65.5KB min / 18.5KB gzip). Hashes plain JS objects/arrays by
identity: `Map().set([1,2],'v').get([1,2])` is undefined, so every squint
composite key would need I.List/I.Map wrapping at each boundary (and loses
2x to raw vectors even then). `I.is` never equates a plain object. Fills no
squint slots: `pr-str` prints `#<Map>` and core `assoc` falls through to
the object path and mutates the instance.

### Tree-shakable JS HAMTs (measured, Map-only entry)

| library | min/gzip | tree-shakes? |
|---|---|---|
| @seedtactics/immutable-collections `/hamt` | 6.6KB / 2.1KB | yes (lookup only: 1.9KB) |
| same lib, HashMap class facade | 48.7KB / 11.6KB | no |
| mattbierner hamt / hamt_plus | ~8KB / ~3KB | no, CJS monolith, just small |
| @collectable/map | 47.5KB / 15.5KB | no |
| Immutable.js | 65.5KB / 18.5KB | no |
| @rimbu/hashed | 135.5KB / 29.6KB | no |

seedtactics is the direct prior art: function-based HAMT explicitly because
"bundlers do not tree-shake classes"; their own class facade bloats back to
48.7KB. Squint keeps the class + symbol-slot dispatch and recovers
shakability with the pure-annotation wrappers instead. None of the prior
art hashes against a structural equality over plain JS data; all need a
hash config or their own wrapped types. The trie machinery itself is
byte-comparable (both ~6.6KB); squint's extra ~5KB is value hashing (1.7KB)
plus core deps an app usually has anyway (dequal/iterable/reduced, 3.6KB).

### ham-scripted (cnuernber)

github.com/cnuernber/ham-scripted ports the ham-fisted BitmapTrie to JS:
owner tokens (nodes copy only when `node.owner != nowner`; one assoc path
serves persistent `shallowClone` + transient `mutAssoc`), LeafNode per
entry with cached hashcode and collision chain (splits never rehash, leaf
doubles as entry), two node classes with pow2 over-allocated arrays,
pluggable {hash, equals} provider, cyrb53 uncached string hash, and a
mapProxy like obj-view. His README lesson (numeric hash case before
protocol dispatch, 5x) squint already has via the typeof switch.

Tried the full owner-token + LeafNode rewrite here (July 2026): bundle
shrank ~0.5KB and transient batch build won (55ms vs 83ms, beating
withMutations at 67ms that run), but every persistent op lost: build 96 ->
202ms, get hit 34 -> 56ms, dissoc 84 -> 203ms (same-process comparison).
Causes: no dense ArrayNode tier (bitpos+popcount at every level where CLJS
array-indexes), a pointer chase per entry, pow2 slack enlarging every
persistent path copy, owner pointers retaining dead intermediate maps. The
design is tuned for mutable-first workloads; squint is persistent-first.
Reverted; kept the string-cache bump (1024 -> 8192). Still-open ports from
that comparison: CLJS-style transient node editing (inode-assoc! family),
per-entry cached hashes if composite-key-heavy maps show up.

### EncMap encoder

See doc/dev/composite-map-keys.md: canonical-string encoding over a native
Map. Competitive on build, loses on miss (always pays a full encode). The
HAMT subsumes it.

## Limitations / next steps

- Transients do path copying (correct, not fast). Port CLJS node editing
  for into-heavy builds.
- `extend-type` with an alias-qualified protocol (`i/IHash`) mis-emits the
  slot key (`i._hash`). Unqualified core protocols work. Compiler bug,
  predates this module.
- No metadata support (`_metaSym` is core-private).
- `assoc!`/`conj!` on the persistent map (not a transient handle) fall
  through to the object mutation path in core and corrupt the instance.
- `(m :k)` in function position compiles to a direct call and throws, same
  as a plain squint map held in a local.
- Only the map exists; set and vector are future work, as is a compiler/ns
  level "immutable mode" where literals emit these structures.

## Tests and demo

- `test/squint/immutable_test.cljs`: 11 deftest-eval programs in the main
  suite covering basics, key edge cases (nil/false/0), composite keys,
  collisions ("Aa"/"BB"), equality + hash consistency, core fns (conj/into/
  merge/merge-with/update/assoc-in/update-in/get-in/select-keys/reduce-kv
  with reduced), transients incl. invalidation, printing, clj->js,
  obj-view, satisfies?/extend-type IHash, and a 5k-key spill/pack scale
  test.
- Demo: `node node_cli.js run doc/dev/hamt_demo.cljs`.
- JS-level smoke: `node doc/dev/hamt_smoke.js`.

## Attribution

The trie is ported from ClojureScript's PersistentHashMap (cljs/core.cljs),
Copyright (c) Rich Hickey and contributors, EPL 1.0, after Phil Bagwell's
"Ideal Hash Trees". The hash section ports CLJS's Murmur3 use (MurmurHash3
by Austin Appleby, public domain). Both carry source headers. Squint is EPL
1.0, so licenses match.
