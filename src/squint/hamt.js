// Optional persistent hash map (HAMT), ported from CLJS PersistentHashMap.
// Not part of core: import it explicitly. Instances plug into core's
// INSTANCE_TYPE protocol slots, so get/assoc/dissoc/conj/count/seq/reduce-kv/
// contains?/=/into/merge from core.js work unchanged.
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
  IEditableCollection,
  IEditableCollection__as_transient,
  ITransientCollection__conj_BANG_,
  ITransientCollection__persistent_BANG_,
  ITransientAssociative__assoc_BANG_,
  ITransientMap__dissoc_BANG_,
  _EQ_,
  vector_QMARK_,
  iterable,
  reduced,
  reduced_QMARK_,
  pr_str,
  hash,
  hash_unordered_coll,
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
    return new PersistentHashMap(m.hasNil ? m.cnt : m.cnt + 1, m.root, true, v);
  }
  const addedLeaf = { val: false };
  const newRoot = (m.root == null ? EMPTY_NODE : m.root).inodeAssoc(0, hash(k), k, v, addedLeaf);
  if (newRoot === m.root) return m;
  return new PersistentHashMap(addedLeaf.val ? m.cnt + 1 : m.cnt, newRoot, m.hasNil, m.nilVal);
}

function mapDissoc(m, k) {
  if (k == null) return m.hasNil ? new PersistentHashMap(m.cnt - 1, m.root, false, null) : m;
  if (m.root == null) return m;
  const newRoot = m.root.inodeWithout(0, hash(k), k);
  if (newRoot === m.root) return m;
  return new PersistentHashMap(m.cnt - 1, newRoot, m.hasNil, m.nilVal);
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
  if (vector_QMARK_(x)) {
    if (x.length !== 2) throw new Error('conj on a map takes map entries or seqables of map entries');
    return mapAssoc(m, x[0], x[1]);
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
  p[IEmptyableCollection__empty] = () => EMPTY;
  p[IEquiv__equiv] = mapEquiv;
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
  for (const proto of [ILookup, IAssociative, IMap, ICounted, IKVReduce, ICollection, IEmptyableCollection, IEquiv, IEditableCollection, IEncodeJS, IHash]) {
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
