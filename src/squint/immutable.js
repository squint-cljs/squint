// Optional persistent hash map (HAMT), ported from CLJS PersistentHashMap.
// Not part of core: import it explicitly. Instances plug into core's
// INSTANCE_TYPE protocol slots, so get/assoc/dissoc/conj/count/seq/reduce-kv/
// contains?/=/into/merge from core.js work unchanged.
// The HAMT node structure and algorithms are ported from ClojureScript's
// PersistentHashMap (cljs/core.cljs), Copyright (c) Rich Hickey and
// contributors, Eclipse Public License 1.0, after Phil Bagwell's
// "Ideal Hash Trees".
import {
  ILookup,
  ILookup__lookup,
  IAssociative,
  IAssociative__assoc,
  IAssociative__contains_key_QMARK_,
  IMap,
  IMap__dissoc,
  ICounted,
  ICounted__count,
  IKVReduce,
  IKVReduce__kv_reduce,
  ICollection,
  ICollection__conj,
  IEmptyableCollection,
  IEmptyableCollection__empty,
  IEquiv,
  IEquiv__equiv,
  IEncodeJS,
  IEncodeJS__clj__GT_js,
  IMeta,
  IMeta__meta,
  IWithMeta,
  IWithMeta__with_meta,
  IEditableCollection,
  IEditableCollection__as_transient,
  ITransientCollection__conj_BANG_,
  ITransientCollection__persistent_BANG_,
  ITransientAssociative__assoc_BANG_,
  ITransientMap__dissoc_BANG_,
  ITransientVector__pop_BANG_,
  ISet,
  ISet__disjoin,
  ITransientSet__disjoin_BANG_,
  IStack,
  IStack__peek,
  IStack__pop,
  IIndexed,
  IIndexed__nth,
  IVector,
  _EQ_,
  vector_QMARK_ as core_vector_QMARK_,
  set_QMARK_ as core_set_QMARK_,
  sequential_QMARK_,
  iterable,
  reduced,
  reduced_QMARK_,
  pr_str,
  hash,
  hash_unordered_coll,
  hash_ordered_coll,
  IHash,
  IHash__hash,
} from './core.js';

// hashing lives in core.js next to = (the (= a b) implies (hash a) === (hash b)
// contract couples them); re-exported here for existing requires
export { hash, hash_ordered_coll, hash_unordered_coll, IHash, IHash__hash } from './core.js';

// ---------------------------------------------------------------------------
// HAMT nodes. Straight port of CLJS BitmapIndexedNode / ArrayNode /
// HashCollisionNode, persistent arities only (no transient node editing).
// Node arrays interleave [key, val, ...]; a null key slot means the val slot
// holds a subnode. A nil map key never reaches a node (kept on the map).

const imul = Math.imul;

function mask(h, shift) {
  return (h >>> shift) & 0x01f;
}

function bitpos(h, shift) {
  return 1 << mask(h, shift);
}

function bitCount(x) {
  x -= (x >> 1) & 0x55555555;
  x = (x & 0x33333333) + ((x >> 2) & 0x33333333);
  x = (x + (x >> 4)) & 0x0f0f0f0f;
  return imul(x, 0x01010101) >> 24;
}

function bitIndex(bitmap, bit) {
  return bitCount(bitmap & (bit - 1));
}

function keyTest(key, other) {
  return key === other || _EQ_(key, other);
}

// value-producing ops carry metadata, like CLJS
function keepMeta(src, dst) {
  dst.meta = src.meta;
  return dst;
}

function cloneAndSet(arr, i, a) {
  const clone = arr.slice();
  clone[i] = a;
  return clone;
}

function cloneAndSet2(arr, i, a, j, b) {
  const clone = arr.slice();
  clone[i] = a;
  clone[j] = b;
  return clone;
}

function removePair(arr, i) {
  const newArr = arr.slice(0, 2 * i);
  for (let j = 2 * (i + 1); j < arr.length; j++) newArr.push(arr[j]);
  return newArr;
}

function nodeKvReduce(arr, f, init) {
  for (let i = 0; i < arr.length; i += 2) {
    const k = arr[i];
    if (k != null) {
      init = f(init, k, arr[i + 1]);
    } else {
      const node = arr[i + 1];
      if (node != null) init = node.kvReduce(f, init);
    }
    if (reduced_QMARK_(init)) return init;
  }
  return init;
}

class BitmapIndexedNode {
  constructor(bitmap, arr) {
    this.bitmap = bitmap;
    this.arr = arr;
  }
  inodeAssoc(shift, h, key, val, addedLeaf) {
    const bit = bitpos(h, shift);
    const idx = bitIndex(this.bitmap, bit);
    if ((this.bitmap & bit) === 0) {
      const n = bitCount(this.bitmap);
      if (n >= 16) {
        // unpack into a 32-way ArrayNode
        const nodes = new Array(32).fill(null);
        nodes[mask(h, shift)] = EMPTY_NODE.inodeAssoc(shift + 5, h, key, val, addedLeaf);
        for (let i = 0, j = 0; i < 32; i++) {
          if ((this.bitmap >>> i) & 1) {
            nodes[i] =
              this.arr[j] == null
                ? this.arr[j + 1]
                : EMPTY_NODE.inodeAssoc(shift + 5, hash(this.arr[j]), this.arr[j], this.arr[j + 1], addedLeaf);
            j += 2;
          }
        }
        return new ArrayNode(n + 1, nodes);
      }
      const newArr = this.arr.slice(0, 2 * idx);
      newArr.push(key, val);
      for (let j = 2 * idx; j < this.arr.length; j++) newArr.push(this.arr[j]);
      addedLeaf.val = true;
      return new BitmapIndexedNode(this.bitmap | bit, newArr);
    }
    const keyOrNull = this.arr[2 * idx];
    const valOrNode = this.arr[2 * idx + 1];
    if (keyOrNull == null) {
      const n = valOrNode.inodeAssoc(shift + 5, h, key, val, addedLeaf);
      if (n === valOrNode) return this;
      return new BitmapIndexedNode(this.bitmap, cloneAndSet(this.arr, 2 * idx + 1, n));
    }
    if (keyTest(key, keyOrNull)) {
      if (val === valOrNode) return this;
      return new BitmapIndexedNode(this.bitmap, cloneAndSet(this.arr, 2 * idx + 1, val));
    }
    addedLeaf.val = true;
    return new BitmapIndexedNode(
      this.bitmap,
      cloneAndSet2(this.arr, 2 * idx, null, 2 * idx + 1, createNode(shift + 5, keyOrNull, valOrNode, h, key, val))
    );
  }
  inodeWithout(shift, h, key) {
    const bit = bitpos(h, shift);
    if ((this.bitmap & bit) === 0) return this;
    const idx = bitIndex(this.bitmap, bit);
    const keyOrNull = this.arr[2 * idx];
    const valOrNode = this.arr[2 * idx + 1];
    if (keyOrNull == null) {
      const n = valOrNode.inodeWithout(shift + 5, h, key);
      if (n === valOrNode) return this;
      if (n != null) return new BitmapIndexedNode(this.bitmap, cloneAndSet(this.arr, 2 * idx + 1, n));
      if (this.bitmap === bit) return null;
      return new BitmapIndexedNode(this.bitmap ^ bit, removePair(this.arr, idx));
    }
    if (keyTest(key, keyOrNull)) {
      if (this.bitmap === bit) return null;
      return new BitmapIndexedNode(this.bitmap ^ bit, removePair(this.arr, idx));
    }
    return this;
  }
  inodeLookup(shift, h, key, notFound) {
    const bit = bitpos(h, shift);
    if ((this.bitmap & bit) === 0) return notFound;
    const idx = bitIndex(this.bitmap, bit);
    const keyOrNull = this.arr[2 * idx];
    const valOrNode = this.arr[2 * idx + 1];
    if (keyOrNull == null) return valOrNode.inodeLookup(shift + 5, h, key, notFound);
    return keyTest(key, keyOrNull) ? valOrNode : notFound;
  }
  kvReduce(f, init) {
    return nodeKvReduce(this.arr, f, init);
  }
  *iter() {
    for (let i = 0; i < this.arr.length; i += 2) {
      const k = this.arr[i];
      if (k != null) yield [k, this.arr[i + 1]];
      else if (this.arr[i + 1] != null) yield* this.arr[i + 1].iter();
    }
  }
}

const EMPTY_NODE = /* @__PURE__ */ new BitmapIndexedNode(0, []);

class ArrayNode {
  constructor(cnt, arr) {
    this.cnt = cnt;
    this.arr = arr;
  }
  inodeAssoc(shift, h, key, val, addedLeaf) {
    const idx = mask(h, shift);
    const node = this.arr[idx];
    if (node == null) {
      return new ArrayNode(this.cnt + 1, cloneAndSet(this.arr, idx, EMPTY_NODE.inodeAssoc(shift + 5, h, key, val, addedLeaf)));
    }
    const n = node.inodeAssoc(shift + 5, h, key, val, addedLeaf);
    if (n === node) return this;
    return new ArrayNode(this.cnt, cloneAndSet(this.arr, idx, n));
  }
  inodeWithout(shift, h, key) {
    const idx = mask(h, shift);
    const node = this.arr[idx];
    if (node == null) return this;
    const n = node.inodeWithout(shift + 5, h, key);
    if (n === node) return this;
    if (n == null) {
      if (this.cnt <= 8) return this.packNode(idx);
      return new ArrayNode(this.cnt - 1, cloneAndSet(this.arr, idx, n));
    }
    return new ArrayNode(this.cnt, cloneAndSet(this.arr, idx, n));
  }
  // repack into a BitmapIndexedNode, dropping child idx
  packNode(idx) {
    const newArr = new Array(2 * (this.cnt - 1)).fill(null);
    let j = 1;
    let bitmap = 0;
    for (let i = 0; i < this.arr.length; i++) {
      if (i !== idx && this.arr[i] != null) {
        newArr[j] = this.arr[i];
        bitmap |= 1 << i;
        j += 2;
      }
    }
    return new BitmapIndexedNode(bitmap, newArr);
  }
  inodeLookup(shift, h, key, notFound) {
    const node = this.arr[mask(h, shift)];
    return node == null ? notFound : node.inodeLookup(shift + 5, h, key, notFound);
  }
  kvReduce(f, init) {
    for (const node of this.arr) {
      if (node != null) {
        init = node.kvReduce(f, init);
        if (reduced_QMARK_(init)) return init;
      }
    }
    return init;
  }
  *iter() {
    for (const node of this.arr) {
      if (node != null) yield* node.iter();
    }
  }
}

class HashCollisionNode {
  constructor(collisionHash, cnt, arr) {
    this.collisionHash = collisionHash;
    this.cnt = cnt;
    this.arr = arr;
  }
  findIndex(key) {
    const lim = 2 * this.cnt;
    for (let i = 0; i < lim; i += 2) {
      if (keyTest(key, this.arr[i])) return i;
    }
    return -1;
  }
  inodeAssoc(shift, h, key, val, addedLeaf) {
    if (h !== this.collisionHash) {
      // nest under a bitmap node keyed by this node's hash
      return new BitmapIndexedNode(bitpos(this.collisionHash, shift), [null, this]).inodeAssoc(shift, h, key, val, addedLeaf);
    }
    const idx = this.findIndex(key);
    if (idx === -1) {
      addedLeaf.val = true;
      return new HashCollisionNode(this.collisionHash, this.cnt + 1, [...this.arr, key, val]);
    }
    if (this.arr[idx + 1] === val) return this;
    return new HashCollisionNode(this.collisionHash, this.cnt, cloneAndSet(this.arr, idx + 1, val));
  }
  inodeWithout(shift, h, key) {
    const idx = this.findIndex(key);
    if (idx === -1) return this;
    if (this.cnt === 1) return null;
    return new HashCollisionNode(this.collisionHash, this.cnt - 1, removePair(this.arr, idx / 2));
  }
  inodeLookup(shift, h, key, notFound) {
    const idx = this.findIndex(key);
    return idx === -1 ? notFound : this.arr[idx + 1];
  }
  kvReduce(f, init) {
    return nodeKvReduce(this.arr, f, init);
  }
  *iter() {
    for (let i = 0; i < 2 * this.cnt; i += 2) yield [this.arr[i], this.arr[i + 1]];
  }
}

function createNode(shift, key1, val1, key2hash, key2, val2) {
  const key1hash = hash(key1);
  if (key1hash === key2hash) return new HashCollisionNode(key1hash, 2, [key1, val1, key2, val2]);
  const box = { val: false };
  return EMPTY_NODE.inodeAssoc(shift, key1hash, key1, val1, box).inodeAssoc(shift, key2hash, key2, val2, box);
}

// ---------------------------------------------------------------------------
// the map type

const SENTINEL = {};

function mapLookup(m, k, notFound) {
  if (k == null) return m.hasNil ? m.nilVal : notFound;
  if (m.root == null) return notFound;
  return m.root.inodeLookup(0, hash(k), k, notFound);
}

function mapAssoc(m, k, v) {
  if (k == null) {
    if (m.hasNil && v === m.nilVal) return m;
    return keepMeta(m, new PersistentHashMap(m.hasNil ? m.cnt : m.cnt + 1, m.root, true, v));
  }
  const addedLeaf = { val: false };
  const newRoot = (m.root == null ? EMPTY_NODE : m.root).inodeAssoc(0, hash(k), k, v, addedLeaf);
  if (newRoot === m.root) return m;
  return keepMeta(m, new PersistentHashMap(addedLeaf.val ? m.cnt + 1 : m.cnt, newRoot, m.hasNil, m.nilVal));
}

function mapDissoc(m, k) {
  if (k == null) return m.hasNil ? keepMeta(m, new PersistentHashMap(m.cnt - 1, m.root, false, null)) : m;
  if (m.root == null) return m;
  const newRoot = m.root.inodeWithout(0, hash(k), k);
  if (newRoot === m.root) return m;
  return keepMeta(m, new PersistentHashMap(m.cnt - 1, newRoot, m.hasNil, m.nilVal));
}

function mapContains(m, k) {
  return mapLookup(m, k, SENTINEL) !== SENTINEL;
}

function mapKvReduce(m, f, init) {
  if (m.hasNil) {
    init = f(init, null, m.nilVal);
    if (reduced_QMARK_(init)) return init.value;
  }
  if (m.root != null) {
    init = m.root.kvReduce(f, init);
    if (reduced_QMARK_(init)) return init.value;
  }
  return init;
}

function mapConj(m, x) {
  // a map entry vector assocs; a map/seq of entries folds in, like core conj
  if (core_vector_QMARK_(x)) {
    if (Array.isArray(x)) {
      if (x.length !== 2) throw new Error('conj on a map takes map entries or seqables of map entries');
      return mapAssoc(m, x[0], x[1]);
    }
    // a persistent vector entry
    if (x.cnt !== 2) throw new Error('conj on a map takes map entries or seqables of map entries');
    return mapAssoc(m, x[IIndexed__nth](x, 0), x[IIndexed__nth](x, 1));
  }
  let ret = m;
  for (const e of iterable(x)) ret = mapAssoc(ret, e[0], e[1]);
  return ret;
}

function otherCount(other) {
  if (other instanceof PersistentHashMap) return other.cnt;
  if (other instanceof Map || typeof other.size === 'number') return other.size;
  if (other.constructor === Object || other.constructor === undefined) return Object.keys(other).length;
  return -1;
}

function otherLookup(other, k) {
  if (other instanceof PersistentHashMap) return mapLookup(other, k, SENTINEL);
  if (other instanceof Map || (typeof other.get === 'function' && typeof other.has === 'function')) {
    return other.has(k) ? other.get(k) : SENTINEL;
  }
  return Object.prototype.hasOwnProperty.call(other, k) ? other[k] : SENTINEL;
}

function mapEquiv(m, other) {
  if (m === other) return true;
  if (other == null || typeof other !== 'object') return false;
  if (m.cnt !== otherCount(other)) return false;
  return mapKvReduce(m, (acc, k, v) => {
    const ov = otherLookup(other, k);
    return ov !== SENTINEL && _EQ_(v, ov) ? acc : reduced(false);
  }, true);
}

const PersistentHashMap = /* @__PURE__ */ (() => {
  class PersistentHashMap {
    constructor(cnt, root, hasNil, nilVal) {
      this.cnt = cnt;
      this.root = root;
      this.hasNil = hasNil;
      this.nilVal = nilVal;
      this.meta = null;
      this._hash = null;
    }
    *[Symbol.iterator]() {
      if (this.hasNil) yield [null, this.nilVal];
      if (this.root != null) yield* this.root.iter();
    }
    // core's toEDN print hook
    squint$lang$edn(pr) {
      const parts = [];
      for (const [k, v] of this) {
        parts.push((typeof k === 'string' ? ':' + k : pr(k)) + ' ' + pr(v));
      }
      return '{' + parts.join(', ') + '}';
    }
  }
  const p = PersistentHashMap.prototype;
  p[ILookup__lookup] = mapLookup;
  p[IAssociative__assoc] = mapAssoc;
  p[IAssociative__contains_key_QMARK_] = mapContains;
  p[IMap__dissoc] = mapDissoc;
  p[ICounted__count] = (m) => m.cnt;
  p[IKVReduce__kv_reduce] = mapKvReduce;
  p[ICollection__conj] = mapConj;
  p[IEmptyableCollection__empty] = (m) => (m.meta == null ? EMPTY : p[IWithMeta__with_meta](EMPTY, m.meta));
  p[IEquiv__equiv] = mapEquiv;
  p[IMeta__meta] = (m) => m.meta;
  p[IWithMeta__with_meta] = (m, newMeta) => {
    const c = new PersistentHashMap(m.cnt, m.root, m.hasNil, m.nilVal);
    c._hash = m._hash;
    c.meta = newMeta;
    return c;
  };
  // iterating yields [k v] entry arrays, whose ordered hash equals the map
  // entry hash, so the unordered hash over them matches core's map hashing
  p[IHash__hash] = (m) => {
    if (m._hash === null) m._hash = hash_unordered_coll(m);
    return m._hash;
  };
  p[IEditableCollection__as_transient] = (m) => new TransientHashMap(m);
  // clj->js snapshot: plain object, non-string keys via pr-str like CLJS key->js
  p[IEncodeJS__clj__GT_js] = (m, recur) => {
    const o = {};
    for (const [k, v] of m) {
      o[typeof k === 'string' || typeof k === 'number' ? k : pr_str(k)] = recur(v);
    }
    return o;
  };
  // satisfies? markers
  for (const proto of [ILookup, IAssociative, IMap, ICounted, IKVReduce, ICollection, IEmptyableCollection, IEquiv, IEditableCollection, IEncodeJS, IHash, IMeta, IWithMeta]) {
    p[proto.__sym] = true;
  }
  return PersistentHashMap;
})();

// Transient handle for assoc!/conj!/dissoc!/persistent!. Correctness-only for
// now: it reuses the persistent ops (no node editing), so it invalidates the
// handle like CLJS but does not yet avoid path copying.
const TransientHashMap = /* @__PURE__ */ (() => {
  class TransientHashMap {
    constructor(m) {
      this.m = m;
    }
    ensure() {
      if (this.m == null) throw new Error('Transient used after persistent!');
    }
  }
  const p = TransientHashMap.prototype;
  p[ITransientAssociative__assoc_BANG_] = (t, k, v) => {
    t.ensure();
    t.m = mapAssoc(t.m, k, v);
    return t;
  };
  p[ITransientMap__dissoc_BANG_] = (t, k) => {
    t.ensure();
    t.m = mapDissoc(t.m, k);
    return t;
  };
  p[ITransientCollection__conj_BANG_] = (t, x) => {
    t.ensure();
    t.m = mapConj(t.m, x);
    return t;
  };
  p[ITransientCollection__persistent_BANG_] = (t) => {
    t.ensure();
    const m = t.m;
    t.m = null;
    return m;
  };
  p[ICounted__count] = (t) => (t.ensure(), t.m.cnt);
  p[ILookup__lookup] = (t, k, nf) => (t.ensure(), mapLookup(t.m, k, nf));
  return TransientHashMap;
})();

const EMPTY = /* @__PURE__ */ new PersistentHashMap(0, null, false, null);

export function hash_map(...kvs) {
  if (kvs.length % 2 !== 0) {
    throw new Error('No value supplied for key: ' + kvs[kvs.length - 1]);
  }
  let ret = EMPTY;
  for (let i = 0; i < kvs.length; i += 2) ret = mapAssoc(ret, kvs[i], kvs[i + 1]);
  return ret;
}

export function hash_map_QMARK_(x) {
  return x instanceof PersistentHashMap;
}

// Live read-only plain-object facade for JS APIs that expect a normal object:
// property access, destructuring, spread, Object.keys/entries, JSON.stringify.
// Squint code should keep using the map itself. String keys only; writes throw.
export function obj_view(m) {
  const deny = () => {
    throw new Error('hamt obj-view is read-only');
  };
  return new Proxy(
    {},
    {
      get(_, prop) {
        return typeof prop === 'string' ? mapLookup(m, prop, undefined) : undefined;
      },
      has(_, prop) {
        return typeof prop === 'string' && mapContains(m, prop);
      },
      ownKeys() {
        return mapKvReduce(m, (acc, k) => (typeof k === 'string' && acc.push(k), acc), []);
      },
      getOwnPropertyDescriptor(_, prop) {
        const v = typeof prop === 'string' ? mapLookup(m, prop, SENTINEL) : SENTINEL;
        if (v === SENTINEL) return undefined;
        return { value: v, enumerable: true, configurable: true, writable: false };
      },
      set: deny,
      deleteProperty: deny,
      defineProperty: deny,
    }
  );
}

// ---------------------------------------------------------------------------
// Persistent vector: a 32-way bit-partitioned trie with a tail, ported from
// ClojureScript's PersistentVector (cljs/core.cljs), Copyright (c) Rich
// Hickey and contributors, Eclipse Public License 1.0. Persistent arities
// only; the transient handle wraps the persistent ops like the map's.

class VectorNode {
  constructor(arr) {
    this.arr = arr;
  }
}

const EMPTY_VNODE = /* @__PURE__ */ new VectorNode(/* @__PURE__ */ new Array(32));

function cloneVNode(node) {
  return new VectorNode(node.arr.slice());
}

function tailOff(v) {
  return v.cnt < 32 ? 0 : ((v.cnt - 1) >>> 5) << 5;
}

function newPath(level, node) {
  let ret = node;
  for (let ll = level; ll > 0; ll -= 5) {
    const r = new VectorNode(new Array(32));
    r.arr[0] = ret;
    ret = r;
  }
  return ret;
}

function pushTail(v, level, parent, tailnode) {
  const ret = cloneVNode(parent);
  const subidx = ((v.cnt - 1) >>> level) & 0x01f;
  if (level === 5) {
    ret.arr[subidx] = tailnode;
    return ret;
  }
  const child = parent.arr[subidx];
  ret.arr[subidx] = child != null ? pushTail(v, level - 5, child, tailnode) : newPath(level - 5, tailnode);
  return ret;
}

function uncheckedArrayFor(v, i) {
  if (i >= tailOff(v)) return v.tail;
  let node = v.root;
  for (let level = v.shift; level > 0; level -= 5) {
    node = node.arr[(i >>> level) & 0x01f];
  }
  return node.arr;
}

function arrayFor(v, i) {
  if (i >= 0 && i < v.cnt) return uncheckedArrayFor(v, i);
  throw new Error('No item ' + i + ' in vector of length ' + v.cnt);
}

function doAssoc(level, node, i, val) {
  const ret = cloneVNode(node);
  if (level === 0) {
    ret.arr[i & 0x01f] = val;
    return ret;
  }
  const subidx = (i >>> level) & 0x01f;
  ret.arr[subidx] = doAssoc(level - 5, node.arr[subidx], i, val);
  return ret;
}

function popTail(v, level, node) {
  const subidx = ((v.cnt - 2) >>> level) & 0x01f;
  if (level > 5) {
    const newChild = popTail(v, level - 5, node.arr[subidx]);
    if (newChild == null && subidx === 0) return null;
    const ret = cloneVNode(node);
    ret.arr[subidx] = newChild;
    return ret;
  }
  if (subidx === 0) return null;
  const ret = cloneVNode(node);
  ret.arr[subidx] = null;
  return ret;
}

function vecConj(v, o) {
  if (v.cnt - tailOff(v) < 32) {
    const newTail = v.tail.slice();
    newTail.push(o);
    return keepMeta(v, new PersistentVector(v.cnt + 1, v.shift, v.root, newTail));
  }
  const rootOverflow = (v.cnt >>> 5) > (1 << v.shift);
  const newShift = rootOverflow ? v.shift + 5 : v.shift;
  let newRoot;
  if (rootOverflow) {
    newRoot = new VectorNode(new Array(32));
    newRoot.arr[0] = v.root;
    newRoot.arr[1] = newPath(v.shift, new VectorNode(v.tail));
  } else {
    newRoot = pushTail(v, v.shift, v.root, new VectorNode(v.tail));
  }
  return keepMeta(v, new PersistentVector(v.cnt + 1, newShift, newRoot, [o]));
}

function vecNth(v, n, notFound) {
  if (arguments.length === 2) return arrayFor(v, n)[n & 0x01f];
  if (n >= 0 && n < v.cnt) return uncheckedArrayFor(v, n)[n & 0x01f];
  return notFound;
}

function vecAssocN(v, n, val) {
  if (n >= 0 && n < v.cnt) {
    if (n >= tailOff(v)) {
      const newTail = v.tail.slice();
      newTail[n & 0x01f] = val;
      return keepMeta(v, new PersistentVector(v.cnt, v.shift, v.root, newTail));
    }
    return keepMeta(v, new PersistentVector(v.cnt, v.shift, doAssoc(v.shift, v.root, n, val), v.tail));
  }
  if (n === v.cnt) return vecConj(v, val);
  throw new Error('Index ' + n + ' out of bounds  [0,' + v.cnt + ']');
}

function vecPop(v) {
  if (v.cnt === 0) throw new Error("Can't pop empty vector");
  if (v.cnt === 1) {
    return v.meta == null ? EMPTY_VECTOR : EMPTY_VECTOR[IWithMeta__with_meta](EMPTY_VECTOR, v.meta);
  }
  if (v.cnt - tailOff(v) > 1) {
    return keepMeta(v, new PersistentVector(v.cnt - 1, v.shift, v.root, v.tail.slice(0, -1)));
  }
  const newTail = uncheckedArrayFor(v, v.cnt - 2);
  const nr = popTail(v, v.shift, v.root);
  const newRoot = nr == null ? EMPTY_VNODE : nr;
  if (v.shift > 5 && newRoot.arr[1] == null) {
    return keepMeta(v, new PersistentVector(v.cnt - 1, v.shift - 5, newRoot.arr[0], newTail));
  }
  return keepMeta(v, new PersistentVector(v.cnt - 1, v.shift, newRoot, newTail));
}

function vecKvReduce(v, f, init) {
  let i = 0;
  while (i < v.cnt) {
    const arr = uncheckedArrayFor(v, i);
    for (let j = 0; j < arr.length && i < v.cnt; j++, i++) {
      init = f(init, i, arr[j]);
      if (reduced_QMARK_(init)) return init.value;
    }
  }
  return init;
}

function vecEquiv(v, other) {
  if (v === other) return true;
  if (other == null) return false;
  if (other instanceof PersistentVector) {
    if (v.cnt !== other.cnt) return false;
    for (let i = 0; i < v.cnt; i++) {
      if (!_EQ_(vecNth(v, i), vecNth(other, i))) return false;
    }
    return true;
  }
  if (Array.isArray(other)) {
    if (v.cnt !== other.length) return false;
    for (let i = 0; i < v.cnt; i++) {
      if (!_EQ_(vecNth(v, i), other[i])) return false;
    }
    return true;
  }
  // any other sequential (list, lazy seq): pairwise over iterators
  if (!sequential_QMARK_(other)) return false;
  const vi = v[Symbol.iterator]();
  const oi = other[Symbol.iterator]();
  for (;;) {
    const a = vi.next();
    const b = oi.next();
    if (a.done || b.done) return a.done === b.done;
    if (!_EQ_(a.value, b.value)) return false;
  }
}

const PersistentVector = /* @__PURE__ */ (() => {
  class PersistentVector {
    constructor(cnt, shift, root, tail) {
      this.cnt = cnt;
      this.shift = shift;
      this.root = root;
      this.tail = tail;
      this.meta = null;
      this._hash = null;
    }
    *[Symbol.iterator]() {
      let i = 0;
      while (i < this.cnt) {
        const arr = uncheckedArrayFor(this, i);
        for (let j = 0; j < arr.length && i < this.cnt; j++, i++) yield arr[j];
      }
    }
    // core's toEDN print hook
    squint$lang$edn(pr) {
      const parts = [];
      for (const x of this) parts.push(pr(x));
      return '[' + parts.join(' ') + ']';
    }
  }
  const p = PersistentVector.prototype;
  p[ILookup__lookup] = (v, k, nf) => (typeof k === 'number' ? vecNth(v, k, nf) : nf);
  p[IAssociative__assoc] = (v, k, val) => {
    if (typeof k !== 'number') throw new Error("Vector's key for assoc must be a number.");
    return vecAssocN(v, k, val);
  };
  p[IAssociative__contains_key_QMARK_] = (v, k) => Number.isInteger(k) && k >= 0 && k < v.cnt;
  p[ICounted__count] = (v) => v.cnt;
  p[IIndexed__nth] = vecNth;
  p[ICollection__conj] = vecConj;
  p[IEmptyableCollection__empty] = (v) => (v.meta == null ? EMPTY_VECTOR : p[IWithMeta__with_meta](EMPTY_VECTOR, v.meta));
  p[IEquiv__equiv] = vecEquiv;
  p[IMeta__meta] = (v) => v.meta;
  p[IWithMeta__with_meta] = (v, newMeta) => {
    const c = new PersistentVector(v.cnt, v.shift, v.root, v.tail);
    c._hash = v._hash;
    c.meta = newMeta;
    return c;
  };
  p[IKVReduce__kv_reduce] = vecKvReduce;
  p[IStack__peek] = (v) => (v.cnt > 0 ? vecNth(v, v.cnt - 1) : null);
  p[IStack__pop] = vecPop;
  p[IHash__hash] = (v) => {
    if (v._hash === null) v._hash = hash_ordered_coll(v);
    return v._hash;
  };
  p[IEditableCollection__as_transient] = (v) => new TransientVector(v);
  p[IEncodeJS__clj__GT_js] = (v, recur) => {
    const out = [];
    for (const x of v) out.push(recur(x));
    return out;
  };
  // satisfies? markers
  for (const proto of [IVector, ILookup, IAssociative, ICounted, IIndexed, ICollection, IEmptyableCollection, IEquiv, IKVReduce, IStack, IEditableCollection, IEncodeJS, IHash, IMeta, IWithMeta]) {
    p[proto.__sym] = true;
  }
  return PersistentVector;
})();

// Transient handle wrapping the persistent ops, like TransientHashMap
const TransientVector = /* @__PURE__ */ (() => {
  class TransientVector {
    constructor(v) {
      this.v = v;
    }
    ensure() {
      if (this.v === null) throw new Error('Transient used after persistent!');
    }
  }
  const p = TransientVector.prototype;
  p[ITransientCollection__conj_BANG_] = (t, x) => {
    t.ensure();
    t.v = vecConj(t.v, x);
    return t;
  };
  p[ITransientAssociative__assoc_BANG_] = (t, k, val) => {
    t.ensure();
    if (typeof k !== 'number') throw new Error("Vector's key for assoc must be a number.");
    t.v = vecAssocN(t.v, k, val);
    return t;
  };
  p[ITransientVector__pop_BANG_] = (t) => {
    t.ensure();
    t.v = vecPop(t.v);
    return t;
  };
  p[ITransientCollection__persistent_BANG_] = (t) => {
    t.ensure();
    const v = t.v;
    t.v = null;
    return v;
  };
  p[ICounted__count] = (t) => (t.ensure(), t.v.cnt);
  p[ILookup__lookup] = (t, k, nf) => (t.ensure(), typeof k === 'number' ? vecNth(t.v, k, nf) : nf);
  return TransientVector;
})();

const EMPTY_VECTOR = /* @__PURE__ */ new PersistentVector(0, 5, EMPTY_VNODE, []);

export function vector(...xs) {
  if (xs.length === 0) return EMPTY_VECTOR;
  if (xs.length <= 32) return new PersistentVector(xs.length, 5, EMPTY_VNODE, xs);
  let ret = new PersistentVector(32, 5, EMPTY_VNODE, xs.slice(0, 32));
  for (let i = 32; i < xs.length; i++) ret = vecConj(ret, xs[i]);
  return ret;
}

export function vec(coll) {
  if (coll == null) return EMPTY_VECTOR;
  if (coll instanceof PersistentVector) return coll;
  if (Array.isArray(coll)) return vector(...coll);
  let ret = EMPTY_VECTOR;
  for (const x of iterable(coll)) ret = vecConj(ret, x);
  return ret;
}

export function vector_QMARK_(x) {
  return x instanceof PersistentVector;
}

// ---------------------------------------------------------------------------
// Persistent hash set: a wrapper over the persistent hash map (elements are
// keys mapping to themselves), like ClojureScript's PersistentHashSet
// (cljs/core.cljs), Copyright (c) Rich Hickey and contributors, Eclipse
// Public License 1.0.

function setConj(s, x) {
  const nm = mapAssoc(s.m, x, x);
  return nm === s.m ? s : keepMeta(s, new PersistentHashSet(nm));
}

function setDisj(s, x) {
  const nm = mapDissoc(s.m, x);
  return nm === s.m ? s : keepMeta(s, new PersistentHashSet(nm));
}

function otherSetCount(other) {
  if (other instanceof PersistentHashSet) return other.m.cnt;
  if (typeof other.size === 'number') return other.size;
  return -1;
}

function setEquiv(s, other) {
  if (s === other) return true;
  if (other == null || !core_set_QMARK_(other)) return false;
  if (s.m.cnt !== otherSetCount(other)) return false;
  // iterate the other side, membership-test on this side: our contains is
  // value-based (hash + =), a js/Set .has is reference-based
  for (const x of other) {
    if (!mapContains(s.m, x)) return false;
  }
  return true;
}

const PersistentHashSet = /* @__PURE__ */ (() => {
  class PersistentHashSet {
    constructor(m) {
      this.m = m;
      this.meta = null;
      this._hash = null;
    }
    *[Symbol.iterator]() {
      for (const e of this.m) yield e[0];
    }
    // core's toEDN print hook
    squint$lang$edn(pr) {
      const parts = [];
      for (const x of this) parts.push(pr(x));
      return '#{' + parts.join(' ') + '}';
    }
  }
  const p = PersistentHashSet.prototype;
  // (get s x) returns the stored element, like CLJS
  p[ILookup__lookup] = (s, k, nf) => mapLookup(s.m, k, nf);
  p[IAssociative__contains_key_QMARK_] = (s, k) => mapContains(s.m, k);
  p[ICounted__count] = (s) => s.m.cnt;
  p[ICollection__conj] = setConj;
  p[ISet__disjoin] = setDisj;
  p[IEmptyableCollection__empty] = (s) => (s.meta == null ? EMPTY_SET : p[IWithMeta__with_meta](EMPTY_SET, s.meta));
  p[IEquiv__equiv] = setEquiv;
  p[IMeta__meta] = (s) => s.meta;
  p[IWithMeta__with_meta] = (s, newMeta) => {
    const c = new PersistentHashSet(s.m);
    c._hash = s._hash;
    c.meta = newMeta;
    return c;
  };
  p[IHash__hash] = (s) => {
    if (s._hash === null) s._hash = hash_unordered_coll(s);
    return s._hash;
  };
  p[IEditableCollection__as_transient] = (s) => new TransientHashSet(s.m);
  p[IEncodeJS__clj__GT_js] = (s, recur) => {
    const out = [];
    for (const x of s) out.push(recur(x));
    return out;
  };
  // satisfies? markers
  for (const proto of [ISet, ILookup, ICounted, ICollection, IEmptyableCollection, IEquiv, IEditableCollection, IEncodeJS, IHash, IMeta, IWithMeta]) {
    p[proto.__sym] = true;
  }
  return PersistentHashSet;
})();

// Transient handle wrapping the persistent ops, like the map's and vector's
const TransientHashSet = /* @__PURE__ */ (() => {
  class TransientHashSet {
    constructor(m) {
      this.m = m;
    }
    ensure() {
      if (this.m === null) throw new Error('Transient used after persistent!');
    }
  }
  const p = TransientHashSet.prototype;
  p[ITransientCollection__conj_BANG_] = (t, x) => {
    t.ensure();
    t.m = mapAssoc(t.m, x, x);
    return t;
  };
  p[ITransientSet__disjoin_BANG_] = (t, x) => {
    t.ensure();
    t.m = mapDissoc(t.m, x);
    return t;
  };
  p[ITransientCollection__persistent_BANG_] = (t) => {
    t.ensure();
    const s = new PersistentHashSet(t.m);
    t.m = null;
    return s;
  };
  p[ICounted__count] = (t) => (t.ensure(), t.m.cnt);
  p[ILookup__lookup] = (t, k, nf) => (t.ensure(), mapLookup(t.m, k, nf));
  p[IAssociative__contains_key_QMARK_] = (t, k) => (t.ensure(), mapContains(t.m, k));
  return TransientHashSet;
})();

const EMPTY_SET = /* @__PURE__ */ new PersistentHashSet(EMPTY);

export function hash_set(...xs) {
  if (xs.length === 0) return EMPTY_SET;
  let m = EMPTY_SET.m;
  for (const x of xs) m = mapAssoc(m, x, x);
  return new PersistentHashSet(m);
}

export function set(coll) {
  if (coll == null) return EMPTY_SET;
  if (coll instanceof PersistentHashSet) return coll;
  let m = EMPTY_SET.m;
  for (const x of iterable(coll)) m = mapAssoc(m, x, x);
  return new PersistentHashSet(m);
}

export function hash_set_QMARK_(x) {
  return x instanceof PersistentHashSet;
}
