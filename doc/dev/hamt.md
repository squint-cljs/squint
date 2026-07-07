# squint.immutable: optional persistent hash map

Prototype notes. `src/squint/immutable.js` is an opt-in module with a persistent
HAMT map ported from CLJS PersistentHashMap. Follow-up to
`composite-map-keys.md` (option 3: a real hashed map).

Require as `(:require [squint.immutable :as i])`: the ns is registered in
`library-imports` (compiler_common.cljc) and `lib-modules` (bb/tasks.clj,
generates lib_vars.edn), like squint.string. String requires of the module
path keep working.

## Shape

- `hash-map`, `hash-map?`, `obj-view` are the exports. `hash`,
  `hash-ordered-coll`, `hash-unordered-coll`, `IHash`, `IHash__hash` live in
  core.js (re-exported here) so hash and `=` stay in one file and hash reads
  the `TYPE_TAG`/`SORTED_TAG` brands directly instead of duck-typing sorted
  collections. `(hash x)` is a core var. `=`-only bundles stay hash-free
  (2169 bytes, no murmur constants, verified).
- Instances carry no `TYPE_TAG`, so core dispatch routes them through the
  INSTANCE_TYPE extension path. The prototype fills the existing slots:
  ILookup, IAssociative, IMap, ICounted, IKVReduce, ICollection,
  IEmptyableCollection, IEquiv, IEditableCollection, plus transient slots on a
  handle type. `get`/`assoc`/`dissoc`/`conj`/`count`/`seq`/`contains?`/
  `reduce-kv`/`=`/`into`/`merge`/`merge-with` work from core unchanged.
- The `IHash` slot symbol is named `IHash_-hash`, matching defprotocol
  emission, so `(extend-type T IHash (-hash [x] ...))` fills it. As a core
  protocol it resolves without any `:refer`.
- Hashing is Murmur3 like CLJS: string hash cache, hash-ordered-coll for
  sequential iterables, hash-unordered-coll for sets and map entries. The
  contract is `(= a b)` implies `(hash a) === (hash b)` against core `dequal`:
  vector, list and lazy seq of equal elements hash alike, plain object and
  js/Map with equal entries hash alike.
- Nodes are the CLJS trio: BitmapIndexedNode (spills to ArrayNode past 16
  children), ArrayNode (packs back below 8), HashCollisionNode. Persistent
  arities only. The transient handle reuses the persistent ops, so
  `transient`/`assoc!`/`persistent!` are correct but do no node editing yet.

## JS interop

- `clj->js` dispatches through a new core `IEncodeJS` protocol
  (`IEncodeJS__clj__GT_js` slot), like CLJS. The hamt impl snapshots to a
  plain object, deep, with non-string keys stringified via `pr-str` like CLJS
  `key->js`. The impl receives a cycle-safe recur fn as second arg.
- `obj-view` wraps a map in a live read-only Proxy for JS APIs that expect a
  plain object: property access, destructuring, spread, `Object.keys`,
  `JSON.stringify`, `in` all work, writes throw. String keys only. Reads cost
  ~1.5x a hamt `get`. Squint code should use the map itself, not the view.

## core.js changes

`IEncodeJS` protocol added (two exports, in core.edn via bump-core-vars),
`clj->js` checks its slot first. Four further internal edits, no new exports:

- `dequal` dispatches `-equiv` on the right argument when only that side has
  it, so `(= {:a 1} (hamt/hash-map :a 1))` holds in both orders.
- `seq` materializes distinct tagged entries for an instance with an
  `IMap__dissoc` slot, like the js/Map branch.
- `toEDN` calls a `squint$lang$edn` method when a value has one. hamt maps
  print as `{:a 1, :b 2}`.
- `map?` is true for instances marked with `IMap.__sym`.

Cost for apps that do not use hamt: +228 bytes minified on a 10-fn core mix
(13403 -> 13631). The `identity` floor is unchanged.

## DCE cost

esbuild 0.28, `--bundle --minify --format=esm`:

| entry                              | min bytes | gzip |
|------------------------------------|-----------|------|
| core 10-fn mix                     | 13631     | 4722 |
| core 10-fn mix + hamt hash-map     | 22879     | 7670 |
| hamt hash-map only                 | 16368     | 5676 |
| hamt hash fn only                  |  1716     |  858 |

Marginal cost of the map for an app already on core: ~9.2KB min / ~2.9KB
gzip. The hash fns tree-shake independently of the map type. The hamt-only
number includes the pr-str/toEDN chain pulled by the clj->js slot; an app
that prints anything pays that once.

## vs Immutable.js

Immutable.js 5.1.9 is the same data structure (32-way HAMT) with different
semantics and cost. Measured node v22, N=200k string keys, 50k vector keys.

| | hamt.js | Immutable.js |
|---|---|---|
| bundle, Map only | 11.9KB min / 4.2KB gzip | 65.5KB min / 18.5KB gzip |
| build (persistent set) | 106 ms | 53 ms |
| build (batch) | 83 ms (path-copying) | 27 ms (withMutations) |
| get hit | 30 ms | 15 ms |
| get miss | 53 ms | 10 ms |
| delete all | 75 ms | 55 ms |
| composite key assoc+get | 23 ms (raw vectors) | 52 ms (I.List wrap) |

- Immutable.js does not tree-shake: Map-only import pays the full 65KB class
  graph. hamt.js rides the package `sideEffects: false` + pure-annotation
  setup, and the hash fns split off at 1.7KB.
- Key semantics: Immutable.js hashes plain JS objects and arrays by identity,
  so `Map().set([1,2],'v').get([1,2])` is undefined. Squint data is plain
  arrays and objects, so every composite key would need wrapping in I.List /
  I.Map at each boundary. hamt.js hashes by value against core `dequal`, raw
  arrays work, and beats the wrapped version 2x on the composite-key bench.
- Equality: `I.is` only equates Immutable collections, never a plain object.
  hamt maps equal plain objects and js/Maps both ways through core `=`.
- Protocols: Immutable types fill no squint slots. `count`/`get` happen to
  work (`size`/`.get` duck paths), `pr-str` prints `#<Map>`, and `assoc`
  silently falls through to the object path and mutates the instance. An
  adapter filling the slots on Immutable prototypes is possible but keeps
  identity keys and drags the 65KB regardless.
- The 2-3x speed gap on string keys is real node editing in withMutations,
  a bigger string-hash cache, and years of tuning. The composite-key case,
  the reason this module exists, already favors hamt.js.

## Limitations / next steps

- Transients do path copying (correct, not fast). Port node editing for
  `into`-heavy builds.
- `extend-type` with an alias-qualified protocol (`hamt/IHash`) mis-emits the
  slot key as `hamt._hash`. `:refer [IHash IHash__hash]` works. Compiler bug,
  predates this module.
- No metadata support (`_metaSym` is core-private).
- `assoc!`/`conj!` on the persistent map (not a transient handle) fall through
  to the object mutation path in core and corrupt the instance.
- `(m :k)` in function position compiles to a direct call and throws, same as
  a plain squint map held in a local.
- No benchmarks yet against EncMap from `composite-map-keys.md` or CLJS.

Demo: `node node_cli.js run doc/dev/hamt_demo.cljs`. Smoke assertions:
`node doc/dev/hamt_smoke.js`, to be turned into real tests.
