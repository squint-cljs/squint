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
- Key equality is `equiv` (identical or -equiv), so hashing satisfies
  `(equiv a b)` implies `(hash a) === (hash b)`: persistent values by value,
  plain mutable data by reference (uid).

## Decision

### Equiv keying (July 2026, supersedes earlier equality claims)

Keys and elements compare by `equiv`, not `=`. Persistent values are
value-semantic (their -equiv, cached value hashes); plain mutable data
(arrays, objects, js Maps/Sets) is reference-semantic (uid hashes, stable
under mutation - the mutable-key footgun is structurally gone). The
consequences, matching CLJS's treatment of #js data:

- a persistent collection never equals plain data: `(= (i/hash-map :a 1)
  {:a 1})` and `(= (i/vector 1 2) [1 2])` are false, like
  `(= [1 2] #js [1 2])` in CLJS
- composite value keys are persistent values: `(assoc m (i/vector 1 2) :v)`
  probed with an equal `(i/vector 1 2)`; a raw `[1 2]` key stores and
  probes by reference
- imm-collection equality is equiv all the way down: nested plain values
  compare by reference
- the map's own hash combines per-entry ordered [k v] hashes via
  `mix-collection-hash` (entry pair arrays must not be hashed as plain
  arrays, which would uid them)
- printing goes through IPrintWithWriter; the recursive printer arrives in
  opts under :pr (squint extension), keeping pr-str out of the module

Any earlier statement in this ADR that hamt keys follow dequal, that raw
arrays work as value keys, or that persistent and plain collections
compare equal, is superseded by this section.

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
nil-key slot on the map object. Transients do real node editing, CLJS
style: the transient handle is the edit token, nodes owned by it mutate in
place (inodeAssocBang/inodeWithoutBang; splice-based in-place inserts
instead of CLJS's preallocated slack). Same for the vector
(tvPushTail/tvPopTail/doAssocBang, owned tail) and the set (wraps the
transient map). Measured: map transient build is ~2x the persistent fold
in-process; vector transient build 200k = 21ms (path-copying was 87ms,
Immutable.js withMutations 23ms).

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

### Context-dependent real keywords: considered and rejected

Idea: keep `{:a 1}` literal keys as strings (the compiler knows the
context) but emit an interned Keyword object with a precomputed hash for
value positions like `(assoc {} :a 1)`, stringifying via
toString/Symbol.toPrimitive so property access coerces; the hamt could
then keep keywords apart from strings. Coercion does carry the write
paths (`o[kw]` works, the `(:a json)` story survives), and interning
would buy Immutable.js's string-key edge. Rejected on three seams:

- Write/read asymmetry: object keys are irreversibly strings, so
  Object.keys/iteration/seq return `"a"` with no memory of keywordness,
  and every read boundary of the default rep erases the distinction.
- The equality partition (the crux): `(= (i/hash-map :a 1) {:a 1})` and
  ":a distinct from \"a\" inside the hamt" cannot both hold. Keyword
  different from string makes the same literal syntax mean different keys
  per collection type; keyword equal to string rebuilds
  keywords-are-strings with extra allocations. Equality is a partition -
  "distinct, but only in some collections" is a contradiction, not a
  smaller feature.
- Non-coercing JS: js/Map and js/Set use SameValueZero (keyword and
  string keys silently diverge), `case` compiles keyword literals to
  string cases, and `(= :a "a")` flips truth value under existing squint
  code.

Decomposed motivations have cheaper answers: hot-key hashing wants
per-entry cached hashes (ham-scripted lesson, listed as headroom), not a
second key type; `keyword?`/EDN round-trip fidelity is the cherry value
proposition (real keywords everywhere, one equality universe). If real
keywords ever happen it is an everywhere decision, not a per-context one.

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

| entry (after vector/set/meta/transients) | min bytes | gzip |
|------------------------------------|-----------|------|
| core `identity` floor              |    16     |   50 |
| core 10-fn mix                     | 13688     | 4738 |
| map only                           | 20513     | 6629 |
| vector only                        | 11221     | 3964 |
| map + vector + set                 | 28219     | 8635 |
| hash fns only                      |  1739     |  898 |

Marginal cost for an app already on core: ~8.6KB min / ~2.9KB gzip. Apps
not using the module pay +228 bytes for the core edits; `=`-only bundles
contain no murmur constants (verified by grep). The hamt-only figure
includes the pr-str/toEDN chain pulled by the clj->js slot. Gotcha found on
the way: `new Int32Array(f64.buffer)` as a top-level const pins the buffer
into every bundle even under a pure annotation (the `.buffer` property read
could be a getter); both views are built in one pure IIFE.

### Performance (node v22, 200k string keys, 50k composite keys)

Same machine, same session, post-equiv. CLJS is advanced-compiled to a
node target. Composite keys are `(i/vector i (inc i))` on the squint side,
persistent vectors on the CLJS side, I.List on the Immutable side.

| | squint | CLJS advanced | Immutable.js 5.1.9 |
|---|---|---|---|
| persistent build | 185 ms | 193 ms | 96 ms |
| transient build | 68 ms | 61 ms | 35 ms |
| get hit | 51 ms | 48 ms | 27 ms |
| get miss | 98 ms | 106 ms | 15 ms |
| dissoc all | 124 ms | 142 ms | 88 ms |
| composite key assoc+get | 74 ms | 40 ms | 90 ms |

Parity with advanced CLJS on the string-key ops and real transients now
within 10%. Immutable.js keeps its string-key lead and stays behind both
CLJS-family maps on composite keys.

### Bundle cost (esbuild, min/gzip, post-equiv)

| entry | min bytes | gzip |
|---|---|---|
| core identity floor | 16 | 48 |
| core 10-fn mix | 13823 | 4798 |
| map only | 18927 | 6217 |
| vector only | 9385 | 3453 |
| map + vector + set | 26171 | 8155 |

Immutable.js Map-only import: 65.5KB min / 18.5KB gzip.

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

## Divergences from compiled CLJS (July 2026 audit)

Compared against the compiled CLJS standard library the squint compiler
itself runs on (lib/cljs-runtime/cljs.core.js). Facts cited from that
output.

Intentional (the equiv design, settled):

- CLJS equiv-map accepts any non-record map (count + reduce-kv with a
  never-equiv sentinel); ours is same-type-only. Same for sequential
  equality (CLJS vectors equal lists/lazy seqs).
- CLJS hashes plain JS objects by goog/getUid; same mechanism as our uid.

Structural / performance:

- No PersistentArrayMap tier. CLJS small maps are flat arrays with linear
  scans, promoting to PHM at HASHMAP_THRESHOLD = 8. Every squint map pays
  HAMT machinery from the first entry.
- seq/keys/vals materialize eager arrays; CLJS returns lazy node-walking
  seq types (NodeSeq, ChunkedSeq, KeySeq/ValSeq) with O(1) first/rest.
- Iterators are generators; CLJS ships handwritten iterator classes
  (RangedIterator). Generators cost ~2-3x per element.
- No direct IReduce; reduce goes through the iterator per element where
  CLJS runs chunked 32-wide array loops.
- String-hash cache: CLJS 1024 entries, ours 8192.

Missing surface:

- Collections are not callable ((v 1), (m :k), (s x) throw); CLJS types
  implement call/apply. Compiler work, not library work.
- No Subvec view (subvec copies O(n)), no MapEntry type (tagged arrays),
  no IFind: `find` on a map with a nil value misses (core find tests get
  against undefined) - a correctness bug, not a feature gap.
- No RSeq/IReversible, no IComparable on vectors (CLJS vectors sort), no
  PersistentQueue, no persistent sorted map/set (squint's sorted-map/set
  are mutable, outside the equiv value world).

Plain-data seam:

- select-keys/zipmap/frequencies/group-by return plain objects from
  persistent inputs (type loss); clojure.set/join accumulates a js Set.
  Should be one policy decision, not per-fn drift.

### Follow-up priorities (importance x expected DCE cost)

| item | importance | expected DCE cost | note |
|---|---|---|---|
| find with nil value (core fix) | done (July 2026) | ~0 | superseded by presence semantics, see ADR 0005 |
| PersistentArrayMap tier | high (perf: most maps are small) | ~1-1.5KB map bundle | semantics-invisible, promote at 8 |
| producer-fn type policy (select-keys etc.) | high (API coherence) | ~0.3KB core | one decision, several fns |
| collections as functions | high (usability) | ~0 lib (compiler) | needs call-position support |
| direct IReduce with chunked loops | medium (perf) | ~0.5KB | biggest for reduce-heavy code |
| handwritten iterators over generators | medium (perf) | ~0, possibly smaller | measure first |
| lazy seq types (NodeSeq/ChunkedSeq/KeySeq/ValSeq) | medium | +2-3KB | only wins on partial consumption of big colls |
| MapEntry type + IFind | medium | ~0.5KB | interacts with core map-entry? |
| Subvec view | low (on demand) | ~0.8KB | subvec correctness is fine today |
| RSeq/IReversible, IComparable on vectors | low | ~0.7KB | demand-driven |
| PersistentQueue, persistent sorted map/set | low | large | separate efforts |
| string-hash cache tuning | done (8192) | 0 | revisit only with data |

## Limitations / next steps

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
