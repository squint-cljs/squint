# Composite (value-equal) map keys

Notes on supporting value-semantic composite keys (vectors, lists, nested maps,
sets) in squint maps, and benchmarks against CLJS persistent maps.

## Problem

Squint maps are plain JS objects, keys are strings. JS object keys can only be
strings or numbers, so composite keys are impossible there. Native `Map`/`Set`
accept any key but compare objects by reference, not value:

```js
const m = new Map([[[1,2], 'a']]);
m.get([1,2]); // undefined - different array ref
```

Current squint handles non-string keys only via explicit `new js/Map(...)`, and
matches keys by a linear `dequal` scan (`core.js` `findKey`, lines 39-43, used by
`dequal` lines 76-90). That is O(n) per lookup, O(n^2) for a full pass. Not
viable at scale.

To get value semantics you need one of:

1. Encode the composite to a canonical primitive (string), back with object/Map.
2. Intern composites so equal ones share a reference, back with native Map.
3. A real hashed map (hash + bucket + equiv).

## Why a custom encoder, not JSON

`JSON.stringify` is native and fast but wrong for this:

- It only walks arrays. A squint LazySeq is an iterable object, serialized as
  `{}` garbage.
- `dequal` (`core.js` lines 109-122) treats a vector, list, and realized lazy
  seq with the same elements as equal. JSON cannot make them encode identically.

The encoder must mirror `dequal` exactly:

> `(= a b)`  <=>  `encode(a) === encode(b)`

A bencode-style length-prefixed scheme gives this with no escaping. Length
prefixes remove ambiguity cheaply (JSON's slow part is string quoting/escaping).
Sorting is only needed for unordered keys (maps, sets); vectors are
order-significant and skip it. Bencode dicts are defined as sorted, but the
encoding is private so the rule is dropped except where correctness needs it.

## Prototype encoder

Two variants. `string-concat` for the common vector + primitive path,
`fragment-array` fallback for maps/sets needing a sort.

```js
function encInto(x, out) {
  if (x === null || x === undefined) { out.push('_'); return; }
  switch (typeof x) {
    case 'number':  out.push('i', x, 'e'); return;
    case 'boolean': out.push(x ? 't' : 'f'); return;
    case 'string':  out.push(x.length, ':', x); return;   // length-prefixed, no escaping
  }
  if (Array.isArray(x)) {                                  // vector
    out.push('l');
    for (let i = 0; i < x.length; i++) encInto(x[i], out);
    out.push('e');
    return;
  }
  if (typeof x[Symbol.iterator] === 'function' && !(x instanceof Map) && !(x instanceof Set)) {
    out.push('l');                                         // lazy seq / list -> same form as vector
    for (const e of x) encInto(e, out);
    out.push('e');
    return;
  }
  if (x instanceof Set) {                                  // sorted (unordered key)
    const parts = [];
    for (const e of x) { const o = []; encInto(e, o); parts.push(o.join('')); }
    parts.sort();
    out.push('s'); for (const p of parts) out.push(p); out.push('e');
    return;
  }
  if (x instanceof Map) {                                  // sorted (unordered key)
    const parts = [];
    for (const [k, v] of x) { const o = []; encInto(k, o); encInto(v, o); parts.push(o.join('')); }
    parts.sort();
    out.push('d'); for (const p of parts) out.push(p); out.push('e');
    return;
  }
  const keys = Object.keys(x).sort();                      // plain object as map
  out.push('d');
  for (const k of keys) { out.push(k.length, ':', k); encInto(x[k], out); }
  out.push('e');
}
function encKey(x) { const out = []; encInto(x, out); return out.join(''); }

function encKeyStr(x) {                                     // common path, no fragment array
  if (x === null || x === undefined) return '_';
  switch (typeof x) {
    case 'number':  return 'i' + x + 'e';
    case 'boolean': return x ? 't' : 'f';
    case 'string':  return x.length + ':' + x;
  }
  if (Array.isArray(x)) {
    let s = 'l';
    for (let i = 0; i < x.length; i++) s += encKeyStr(x[i]);
    return s + 'e';
  }
  return encKey(x);                                         // fall back for rare types
}
```

Map backed by native `Map` keyed on the encoded string, storing `[origKey, val]`
so iteration roundtrips without decoding:

```js
class EncMap {
  constructor(enc) { this.m = new Map(); this.enc = enc || encKey; }
  set(k, v) { this.m.set(this.enc(k), [k, v]); return this; }
  get(k)    { const e = this.m.get(this.enc(k)); return e === undefined ? undefined : e[1]; }
  has(k)    { return this.m.has(this.enc(k)); }
  *keys()   { for (const e of this.m.values()) yield e[0]; }
}
```

Correctness invariants (asserted, all pass):

- `encKey([1,2]) === encKey([1,2])`
- `encKey([1,2]) === encKey(gen 1,2)` (vector == lazy seq, matches `dequal`)
- `encKey([1,2]) !== encKey([2,1])` (order significant)
- `encKey(['12']) !== encKey([1,2])` (no string/number collision)
- `encKey({a,b}) === encKey({b,a})` (map key order canonical)

## Squint demo

`composite_map_keys_demo.cljs` is the same prototype in squint cljs, as a
`defclass EncMap` with map-like methods (assoc/lookup/contains/dissoc/count/
keys/vals/entries) over a backing `js/Map`, lazyseq aware. Self-contained, no
squint internals, runnable in the playground or via `node node_cli.js run`. A
lazy seq, list, and vector with the same elements all encode identically, so a
lookup with a lazy seq hits a vector-keyed entry.

Note: `defclass` cannot express a `[Symbol.iterator]` method (it munges the
method name), so the demo attaches the iterator on the prototype after the
class. Worth fixing in `defclass.cljc` `emit-object-fn`: treat a vector method
name as a computed key. The optimized in-squint version (sibling to
`sorted_set.js`, carrying `TYPE_TAG = MAP_TYPE`) integrates with core dispatch
directly instead.

## Benchmarks

node v22, Apple silicon. Keys `[i, i+1]`. EncMap with `string-concat` encoder
vs CLJS persistent map, transient build, same machine.

| op | proto JS | CLJS native | ratio |
|---|---|---|---|
| N=200k build | 45 ms | 59 ms | proto 1.3x |
| N=200k lookup hit | 38 ms | 24 ms | CLJS 1.6x |
| N=200k lookup miss | 35 ms | 39 ms | par |
| N=1M build | 286 ms | 491 ms | proto 1.7x |
| N=1M lookup hit | 350 ms | 405 ms | proto 1.2x |
| N=1M lookup miss | 410 ms | 276 ms | CLJS 1.5x |

Encoder microbench (encode N keys, no map): `string-concat` 10 ms vs
`fragment-array` 19 ms at 200k. Concat is ~2x faster on the common path.

## Read

Prototype is in the same ballpark as CLJS persistent maps. Wins build, par on
lookup, loses miss at scale, for ~60 lines vs CLJS's full HAMT. Against the
current linear `dequal` scan (not runnable at 1M) it is no contest.

CLJS wins lookup miss because it hashes once and short-circuits on hash/bucket
mismatch. The prototype always builds the full encoded string, so a miss pays
full encode for nothing.

Levers to close the gap:

- Cheaper encode is the biggest knob. Drop the `[origKey, val]` store (decode for
  iteration instead) to cut build alloc.
- Numeric hash instead of string for primitive/small-vector keys: encode to an
  int, native Map on number, no string alloc. Needs a collision fallback (bucket
  + dequal), which drifts toward a real HAMT.
- Miss-heavy workloads benefit from a quick reject (length or first-elem
  precheck) before full encode.

## Open design questions

- Opt-in dedicated map type, or backing for `js/Map` when non-string keys appear
  (keeps the plain-object fast path for the string-key 99% case).
- `keys`/`vals`/`seq`/`=` must route through the type. `dequal` already gives the
  equality contract the encoder mirrors.
- Sentinel prefix needed only if backing with a plain object (so encoded
  composite keys cannot collide with genuine string keys). Native Map backing
  sidesteps it.

## Next steps

1. Wire prototype into `core.js` as a real map type (assoc/get/dissoc/keys/seq/=).
2. Bench encoder variants: numeric-hash vs string, drop `[k,v]` store,
   object-backing + sentinel vs Map-backing.
3. Stress correctness: nested map keys, set keys, lazy-seq keys, mixed, confirm
   encode matches `dequal` throughout.
