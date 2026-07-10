/*eslint no-unused-vars: ["error", { "varsIgnorePattern": "^_", "argsIgnorePattern": "^_", "destructuredArrayIgnorePattern": "^_"}]*/

// __toFn is not public API - the leading underscores mark it as an
// implementation helper shared with other squint runtime modules
// (e.g. multi.js). Signature and semantics may change without notice.
export function __toFn(x) {
  if (x == null || typeof x === 'function') return x;
  if (typeof x === 'string' || isKw(x)) return (coll, d) => get(coll, x, d);
  // a value is callable as a lookup only if it is a collection or a custom
  // type implementing ILookup; a seq, list or opaque object throws when
  // called, like a non-IFn in CLJS
  switch (typeConst(x)) {
    case MAP_TYPE:
    case ARRAY_TYPE:
    case OBJECT_TYPE:
    case SET_TYPE:
      return (k, d) => get(x, k, d);
    case INSTANCE_TYPE:
      if (x[ILookup__lookup] !== undefined) return (k, d) => get(x, k, d);
  }
  return x;
}

// inlined and modified version of https://github.com/lukeed/dequal
var has = Object.prototype.hasOwnProperty;

function findKey(iter, tar, key) {
  for (key of iter.keys()) {
    if (dequal(key, tar)) return key;
  }
}

function isSortedMap(m) {
  return m != null && m[SORTED_TAG] === true && m[TYPE_TAG] === MAP_TYPE;
}
function isSetLike(s) {
  return s != null && (s instanceof Set || s[TYPE_TAG] === SET_TYPE);
}
function isMapLike(m) {
  return (
    m != null &&
    typeof m === 'object' &&
    (m.constructor === Object || m instanceof Map || m[TYPE_TAG] === MAP_TYPE)
  );
}
function mapHas(m, k) {
  if (m instanceof Map || m[TYPE_TAG] === MAP_TYPE) {
    if (m.has(k)) return true;
    const a = altKey(k);
    return a !== undefined && m.has(a);
  }
  return has.call(m, k);
}
function mapGet(m, k) {
  if (m instanceof Map || m[TYPE_TAG] === MAP_TYPE) {
    const v = m.get(k);
    if (v !== undefined) return v;
    const a = altKey(k);
    return a !== undefined ? m.get(a) : undefined;
  }
  return m[k];
}
function mapCount(m) {
  return m instanceof Map || m[TYPE_TAG] === MAP_TYPE
    ? m.size
    : Object.keys(m).length;
}

function dequal(foo, bar) {
  // supports primitives, Array, Set, Map and plain objects
  // like CLJS: does not support NaN
  if (foo === bar) return true;
  // null and undefined are both nil in CLJS, so they compare equal
  if (foo == null) return bar == null;
  if (bar == null) return false;
  // -equiv dispatches on the left argument, like CLJS =
  if (typeof foo === 'object' && foo[IEquiv__equiv] !== undefined) return !!foo[IEquiv__equiv](foo, bar);
  // when only the right side has -equiv (the left is e.g. a plain object),
  // dispatch on it so = stays symmetric
  if (typeof bar === 'object' && bar[IEquiv__equiv] !== undefined) return !!bar[IEquiv__equiv](bar, foo);
  var ctor, len, tmp;

  // A sorted map compares by entries against any map type (object, Map, sorted).
  const fooSorted = isSortedMap(foo);
  if (fooSorted || isSortedMap(bar)) {
    const sm = fooSorted ? foo : bar;
    const other = sm === foo ? bar : foo;
    if (!isMapLike(other) || mapCount(sm) !== mapCount(other)) return false;
    for (const k of sm.keys()) {
      if (!mapHas(other, k) || !dequal(sm.get(k), mapGet(other, k))) return false;
    }
    return true;
  }

  // A plain object and a js/Map are both map reps; compare by entries.
  // Same-type pairs skip this and keep their fast paths below.
  if (isMapLike(foo) && isMapLike(bar) && foo.constructor !== bar.constructor) {
    if (mapCount(foo) !== mapCount(bar)) return false;
    for (const k of foo instanceof Map ? foo.keys() : Object.keys(foo)) {
      if (!mapHas(bar, k) || !dequal(mapGet(foo, k), mapGet(bar, k))) return false;
    }
    return true;
  }

  // Sets (hash or sorted) compare by elements, across concrete types.
  if (isSetLike(foo) || isSetLike(bar)) {
    if (!isSetLike(foo) || !isSetLike(bar) || foo.size !== bar.size) return false;
    for (let e of foo) {
      if (e && typeof e === 'object') {
        e = findKey(bar, e);
        if (!e) return false;
      }
      if (!bar.has(e)) return false;
    }
    return true;
  }

  if (foo && bar && (ctor = foo.constructor) === bar.constructor) {
    if (ctor === Date) return foo.getTime() === bar.getTime();
    // regexes only compare by identity, like CLJS
    if (ctor === RegExp) return false;

    if (ctor === Array) {
      if ((len = foo.length) === bar.length) {
        while (len-- && dequal(foo[len], bar[len]));
      }
      return len === -1;
    }

    if (ctor === Map) {
      if (foo.size !== bar.size) {
        return false;
      }
      for (const kv of foo) {
        tmp = kv[0];
        if (tmp && typeof tmp === 'object') {
          tmp = findKey(bar, tmp);
          if (!tmp) return false;
        }
        if (!dequal(kv[1], bar.get(tmp))) {
          return false;
        }
      }
      return true;
    }

    // LazyIterable falls through to the sequential-equality path below; it is an
    // object but must compare element-wise, not by enumerable properties
    if ((!ctor || typeof foo === 'object') && foo[TYPE_TAG] !== LAZY_ITERABLE_TYPE) {
      len = 0;
      for (const k in foo) {
        if (has.call(foo, k) && ++len && !has.call(bar, k)) return false;
        if (!(k in bar) || !dequal(foo[k], bar[k])) return false;
      }
      return Object.keys(bar).length === len;
    }
  }

  // Cross-type sequential equality, like CLJS `(= '(1 2) [1 2])`: vectors,
  // lists and lazy seqs compare element-wise regardless of concrete type. Only
  // reached when the same-constructor paths above did not apply, so equal-typed
  // collections keep their fast paths.
  if (
    foo && bar &&
    (Array.isArray(foo) || foo[TYPE_TAG] === LAZY_ITERABLE_TYPE) &&
    (Array.isArray(bar) || bar[TYPE_TAG] === LAZY_ITERABLE_TYPE)
  ) {
    const fi = foo[Symbol.iterator]();
    const bi = bar[Symbol.iterator]();
    for (;;) {
      const a = fi.next();
      const b = bi.next();
      if (a.done || b.done) return !!(a.done && b.done);
      if (!dequal(a.value, b.value)) return false;
    }
  }

  return false;
}
// end inlined version of dequals

function walkArray(arr, comp) {
  return arr.every(function (x, i) {
    return i === 0 || comp(arr[i - 1], x);
  });
}

export function _EQ_(...xs) {
  return walkArray(xs, (x, y) => dequal(x, y));
}

export function _GT_(...xs) {
  return walkArray(xs, (x, y) => x > y);
}

export function _GT__EQ_(...xs) {
  return walkArray(xs, (x, y) => x >= y);
}

export function _LT_(...xs) {
  return walkArray(xs, (x, y) => x < y);
}

export function _LT__EQ_(...xs) {
  return walkArray(xs, (x, y) => x <= y);
}

export function _PLUS_(...xs) {
  return xs.reduce((x, y) => x + y, 0);
}

export function _STAR_(...xs) {
  return xs.reduce((x, y) => x * y, 1);
}

export function _(...xs) {
  if (xs.length == 1) {
    return 0 - xs[0];
  }
  return xs.reduce((x, y) => x - y);
}

export function _SLASH_(...xs) {
  if (xs.length === 1) {
    return 1 / xs[0];
  }
  return xs.reduce((x, y) => x / y);
}

export const __protocol_satisfies = {};

export function satisfies_QMARK_(protocol, x) {
  if (x == null) {
    return protocol[null];
  }
  if (typeof protocol == 'symbol') return x[protocol];
  return x[protocol.__sym];
}

function mapAssocMut(m, k, v) {
  m.set(k, v);
  return m;
}

function objAssocMut(m, k, v) {
  m[k] = v;
  return m;
}

function getAssocMut(m) {
  switch (typeConst(m)) {
    case MAP_TYPE:
      return mapAssocMut;
    case ARRAY_TYPE:
    case OBJECT_TYPE:
    case INSTANCE_TYPE:
      return objAssocMut;
  }
}

function validateArrayKeys(o, k, kvs) {
  // like CLJS: a vector key is an index in [0, count], count appends
  let len = o.length;
  for (let i = 0; i < kvs.length + 2; i += 2) {
    const key = i === 0 ? k : kvs[i - 2];
    if (!Number.isInteger(key)) {
      throw new Error("Vector's key for assoc must be a number.");
    }
    if (key < 0 || key > len) {
      throw new Error(`Index ${key} out of bounds [0,${len}]`);
    }
    if (key === len) len++;
  }
}

export function assoc_BANG_(m, k, v, ...kvs) {
  if (arguments.length < 3 || kvs.length % 2 !== 0) {
    throw new Error('Illegal argument: assoc expects an odd number of arguments.');
  }
  switch (typeConst(m)) {
    case MAP_TYPE:
      m.set(k, v);

      for (let i = 0; i < kvs.length; i += 2) {
        m.set(kvs[i], kvs[i + 1]);
      }
      break;
    case ARRAY_TYPE:
      validateArrayKeys(m, k, kvs);
      m[k] = v;

      for (let i = 0; i < kvs.length; i += 2) {
        m[kvs[i]] = kvs[i + 1];
      }
      break;
    case INSTANCE_TYPE:
      if (m[ITransientAssociative__assoc_BANG_] !== undefined) {
        // re-read the slot off the current value: an -assoc! impl may return a
        // different handle
        let ret = m[ITransientAssociative__assoc_BANG_](m, k, v);
        for (let i = 0; i < kvs.length; i += 2) {
          ret = ret[ITransientAssociative__assoc_BANG_](ret, kvs[i], kvs[i + 1]);
        }
        return ret;
      }
    // fall through: an instance without -assoc! keeps the object behavior
    case OBJECT_TYPE:
      m[k] = v;

      for (let i = 0; i < kvs.length; i += 2) {
        m[kvs[i]] = kvs[i + 1];
      }
      break;
    default:
      throw new Error(
        `Illegal argument: assoc! expects a Map, Array, or Object as the first argument, but got ${typeof m}.`
      );
  }

  return m;
}

// value-producing ops (copy, empty, conj, into) carry metadata by
// forwarding the instance-level meta slots onto the fresh structure
function copyMeta(from, to) {
  const f = from?.[IMeta__meta];
  if (f !== undefined) {
    to[IMeta__meta] = f;
    to[IWithMeta__with_meta] = from[IWithMeta__with_meta];
  }
  return to;
}

function copy(o) {
  switch (typeConst(o)) {
    case MAP_TYPE:
      // new o.constructor(o) preserves a SortedMap; for a plain Map it is new Map(o)
      return copyMeta(o, new o.constructor(o));
    case SET_TYPE:
      return copyMeta(o, new o.constructor(o));
    case ARRAY_TYPE:
      return copyMeta(o, [...o]);
    case INSTANCE_TYPE:
    case OBJECT_TYPE:
      return copyMeta(o, { ...o });
    case LIST_TYPE:
      return copyMeta(o, new List(...o));
    default:
      throw new Error(`Don't know how to copy object of type ${typeof o}.`);
  }
}

export function assoc(o, k, v, ...kvs) {
  if (arguments.length < 3 || kvs.length % 2 !== 0) {
    throw new Error('Illegal argument: assoc expects an odd number of arguments.');
  }
  // only nil puns to an empty map; assoc on false throws, like CLJS
  if (o == null) {
    o = {};
  }
  if (o[IAssociative__assoc] !== undefined) {
    let ret = o[IAssociative__assoc](o, k, v);
    for (let i = 0; i < kvs.length; i += 2) {
      ret = ret[IAssociative__assoc](ret, kvs[i], kvs[i + 1]);
    }
    return ret;
  }
  const ret = copy(o);
  assoc_BANG_(ret, k, v, ...kvs);
  return ret;
}

// squint has no distinct hash-map or array-map type; both build a plain object.
export function hash_map(...kvs) {
  if (kvs.length === 0) return {};
  if (kvs.length % 2 !== 0) {
    throw new Error('No value supplied for key: ' + kvs[kvs.length - 1]);
  }
  return assoc({}, ...kvs);
}

export const array_map = hash_map;

const MAP_TYPE = 1;
const ARRAY_TYPE = 2;
const OBJECT_TYPE = 3;
const LIST_TYPE = 4;
const SET_TYPE = 5;
const LAZY_ITERABLE_TYPE = 6;
// a class instance or null-prototype object: the extension point for the
// map-facing protocols. Plain objects keep the OBJECT_TYPE fast path.
const INSTANCE_TYPE = 7;
const KEYWORD_TYPE = 8;

// type tag set in each collection ctor, read by typeConst (DCE: no instanceof).
const TYPE_TAG = Symbol('squint.lang.type');

// brand check, not instanceof, so generic fns don't retain the Keyword class
function isKw(x) {
  return x != null && x[TYPE_TAG] === KEYWORD_TYPE;
}
const SORTED_TAG = Symbol('squint.lang.sorted');

// @__NO_SIDE_EFFECTS__ lets a bundler drop unused defclass/withApply calls; see doc/dev/dce.md
// @__NO_SIDE_EFFECTS__
function defclass(c) {
  return c;
}
// @__NO_SIDE_EFFECTS__
function withApply(f, applyFn) {
  f.squint$lang$variadic = applyFn;
  return f;
}

function emptyOfType(type) {
  switch (type) {
    case MAP_TYPE:
      return new Map();
    case ARRAY_TYPE:
      return [];
    case OBJECT_TYPE:
      return {}; // Object.create?
    case LIST_TYPE:
      return new List();
    case SET_TYPE:
      return new Set();
    case LAZY_ITERABLE_TYPE:
      return lazy(function* () {
        return;
      });
  }
  return undefined;
}

function isObj(coll) {
  return coll.constructor === Object;
}

function isVectorArray(x) {
  return Array.isArray(x) && x[TYPE_TAG] !== LIST_TYPE;
}

export function object_QMARK_(coll) {
  return coll != null && isObj(coll);
}

function typeConst(obj) {
  if (obj == null) {
    return undefined;
  }
  // optimize for object
  if (isObj(obj)) {
    return OBJECT_TYPE;
  }
  if (obj instanceof Map) return MAP_TYPE;
  if (obj instanceof Set) return SET_TYPE;
  // brand, not instanceof, so dispatch does not reference the classes
  const tag = obj[TYPE_TAG];
  // a keyword object is a scalar, not a collection
  if (tag === KEYWORD_TYPE) return undefined;
  if (tag !== undefined) return tag;
  if (isVectorArray(obj)) return ARRAY_TYPE;
  // any remaining object (class instance, null-proto) is associative
  if (typeof obj === 'object') return INSTANCE_TYPE;

  return undefined;
}

function assoc_in_with(f, fname, o, keys, value) {
  keys = vec(keys);
  o = o || {}; // default nil behavior is JS object
  const baseType = typeConst(o);
  if (baseType !== MAP_TYPE && baseType !== ARRAY_TYPE && baseType !== OBJECT_TYPE && baseType !== INSTANCE_TYPE)
    throw new Error(
      `Illegal argument: ${fname} expects the first argument to be a Map, Array, or Object.`
    );

  const chain = [o];
  let lastInChain = o;

  for (let i = 0; i < keys.length - 1; i += 1) {
    const k = keys[i];
    let chainValue;
    if (lastInChain instanceof Map) chainValue = lastInChain.get(k);
    else if (lastInChain != null && lastInChain[ILookup__lookup] !== undefined) {
      chainValue = lastInChain[ILookup__lookup](lastInChain, k, undefined);
    } else chainValue = lastInChain[k];
    if (!chainValue) {
      // an instance root has no empty-of-type: missing levels become plain maps
      chainValue = emptyOfType(baseType) ?? {};
    }
    chain.push(chainValue);
    lastInChain = chainValue;
  }

  chain.push(value);

  for (let i = chain.length - 2; i >= 0; i -= 1) {
    chain[i] = f(chain[i], keys[i], chain[i + 1]);
  }

  return chain[0];
}

export function assoc_in(o, keys, value) {
  return assoc_in_with(assoc, 'assoc-in', o, keys, value);
}

export function assoc_in_BANG_(o, keys, value) {
  keys = vec(keys);
  var currObj = o;
  const baseType = typeConst(o);
  for (const k of keys.splice(0, keys.length - 1)) {
    let v = get(currObj, k);
    if (v === undefined) {
      v = emptyOfType(baseType);
      assoc_BANG_(currObj, k, v);
    }
    currObj = v;
  }
  assoc_BANG_(currObj, keys[keys.length - 1], value);
  return o;
}

export function comp(...fs) {
  fs = fs.map(__toFn);
  if (fs.length === 0) {
    return identity;
  } else if (fs.length === 1) {
    return fs[0];
  }
  const [f, ...more] = fs.slice().reverse();
  return function (...args) {
    let x = f(...args);
    for (const g of more) {
      x = g(x);
    }
    return x;
  };
}

function conj_BANG_set(o, rest) {
  for (const x of rest) {
    o.add(x);
  }
  return o;
}

export function conj_BANG_(...xs) {
  const n = xs.length;
  if (n === 0) {
    return vector();
  }
  // single arg: return the coll unchanged, including nil, like CLJS
  if (n === 1) {
    return xs[0];
  }

  let o = xs[0];
  if (o === null || o === undefined) {
    o = [];
  }

  // Fast path for the common single-element conj! onto an array or set,
  // avoiding the rest-array allocation and spread.
  if (n === 2) {
    switch (typeConst(o)) {
      case ARRAY_TYPE:
        o.push(xs[1]);
        return o;
      case SET_TYPE:
        o.add(xs[1]);
        return o;
    }
  }

  const rest = xs.slice(1);

  switch (typeConst(o)) {
    case SET_TYPE:
      conj_BANG_set(o, rest);
      break;
    case LIST_TYPE:
      o.unshift(...rest.reverse());
      break;
    case ARRAY_TYPE:
      o.push(...rest);
      break;
    case MAP_TYPE:
      for (const x of rest) {
        if (isVectorArray(x)) { asMapEntry(x); o.set(x[0], x[1]); }
        else for (const kv of mapEntriesOf(x)) o.set(kv[0], kv[1]);
      }
      break;
    case INSTANCE_TYPE:
      if (o[ITransientCollection__conj_BANG_] !== undefined) {
        // re-dispatch per element: a -conj! impl may return a different handle
        let acc = o[ITransientCollection__conj_BANG_](o, rest[0]);
        for (let i = 1; i < rest.length; i++) acc = conj_BANG_(acc, rest[i]);
        return acc;
      }
    // fall through: an instance without -conj! keeps the object behavior
    case OBJECT_TYPE:
      for (const x of rest) {
        if (isVectorArray(x)) { asMapEntry(x); o[x[0]] = x[1]; }
        else for (const kv of mapEntriesOf(x)) o[kv[0]] = kv[1];
      }
      break;
    default:
      throw new Error(
        'Illegal argument: conj! expects a Set, Array, List, Map, or Object as the first argument.'
      );
  }

  return o;
}

// entries carried by a non-entry conj arg onto a map: a map merges, a
// seqable must contain entry vectors, like CLJS
function* mapEntriesOf(x) {
  if (isMapLike(x)) {
    yield* iterable(x);
    return;
  }
  for (const kv of iterable(x)) {
    if (!isVectorArray(kv)) {
      throw new Error('conj on a map takes map entries or seqables of map entries');
    }
    yield kv;
  }
}

function asMapEntry(x) {
  if (x.length < 2) {
    throw new Error('Vector arg to map conj must be a pair');
  }
  return x;
}

export function conj(...xs) {
  if (xs.length === 0) {
    return vector();
  }

  const [_o, ...rest] = xs;
  // (conj coll) with nothing to add returns coll unchanged, including nil.
  if (rest.length === 0) return _o;

  let o = _o;
  if (o === null || o === undefined) {
    o = list();
  }
  let m, o2;

  switch (typeConst(o)) {
    case SET_TYPE:
      // brand, not instanceof, so conj does not pin SortedSet
      if (o[SORTED_TAG]) {
        // prevent re-sorting of collection
        return copyMeta(o, conj_BANG_set(new o.constructor(o), rest));
      } else {
        return copyMeta(o, new o.constructor([...o, ...rest]));
      }
    case LIST_TYPE:
      return copyMeta(o, new List(...rest.reverse(), ...o));
    case ARRAY_TYPE:
      return copyMeta(o, [...o, ...rest]);
    case MAP_TYPE:
      m = new Map(o);
      for (const x of rest) {
        if (isVectorArray(x)) { asMapEntry(x); m.set(x[0], x[1]); }
        else for (const kv of mapEntriesOf(x)) m.set(kv[0], kv[1]);
      }

      return copyMeta(o, m);
    case LAZY_ITERABLE_TYPE:
      return lazy(function* () {
        yield* rest;
        yield* o;
      });
    case INSTANCE_TYPE:
      if (o[ICollection__conj] !== undefined) {
        // re-dispatch per element: a -conj impl may return a different type
        o2 = o[ICollection__conj](o, rest[0]);
        for (let i = 1; i < rest.length; i++) o2 = conj(o2, rest[i]);
        return o2;
      }
    // fall through: an instance without -conj keeps the object behavior
    case OBJECT_TYPE:
      o2 = { ...o };

      for (const x of rest) {
        if (isVectorArray(x)) { asMapEntry(x); o2[x[0]] = x[1]; }
        else for (const kv of mapEntriesOf(x)) o2[kv[0]] = kv[1];
      }

      return copyMeta(o, o2);
    default:
      throw new Error(
        'Illegal argument: conj expects a Set, Array, List, Map, or Object as the first argument.'
      );
  }
}

export function disj_BANG_(s, ...xs) {
  if (s != null && s[ITransientSet__disjoin_BANG_] !== undefined) {
    let ret = s;
    for (const x of xs) {
      ret = ret != null && ret[ITransientSet__disjoin_BANG_] !== undefined ? ret[ITransientSet__disjoin_BANG_](ret, x) : disj_BANG_(ret, x);
    }
    return ret;
  }
  for (const x of xs) {
    s.delete(x);
  }
  return s;
}

export function disj(s, ...xs) {
  if (s == null) return s;
  if (xs.length === 0) return s;
  if (s[ISet__disjoin] !== undefined) {
    let ret = s[ISet__disjoin](s, xs[0]);
    for (let i = 1; i < xs.length; i++) ret = disj(ret, xs[i]);
    return ret;
  }
  // pass s itself (not a spread) so a SortedSet keeps its comparator
  const s1 = new s.constructor(s);
  return copyMeta(s, disj_BANG_(s1, ...xs));
}

export function contains_QMARK_(coll, v) {
  if (typeof coll === 'string') {
    return int_QMARK_(v) && v >= 0 && v < coll.length;
  }
  switch (typeConst(coll)) {
    case SET_TYPE:
    case MAP_TYPE: {
      if (coll.has(v)) return true;
      const a = altKey(v);
      return a !== undefined && coll.has(a);
    }
    case undefined:
      return false;
    case INSTANCE_TYPE:
      if (coll[IAssociative__contains_key_QMARK_] !== undefined) {
        return coll[IAssociative__contains_key_QMARK_](coll, v);
      }
    // fall through
    default:
      return v in coll;
  }
}

export function dissoc_BANG_(m, ...ks) {
  if (m != null && m[ITransientMap__dissoc_BANG_] !== undefined) {
    let ret = m;
    for (const k of ks) ret = ret != null && ret[ITransientMap__dissoc_BANG_] !== undefined ? ret[ITransientMap__dissoc_BANG_](ret, k) : dissoc_BANG_(ret, k);
    return ret;
  }
  for (const k of ks) {
    delete m[k];
  }

  return m;
}

export function dissoc(m, ...ks) {
  if (!m) return;
  if (ks.length === 0) return m;
  const tc = typeConst(m);
  if (tc !== MAP_TYPE && tc !== OBJECT_TYPE && tc !== INSTANCE_TYPE) {
    throw new Error('dissoc expects a map, got: ' + typeof m);
  }
  if (tc === INSTANCE_TYPE && m[IMap__dissoc] !== undefined) {
    let ret = m;
    // re-dispatch per key: a -dissoc impl may return a different type
    for (const k of ks) {
      if (ret == null) return ret;
      ret = ret[IMap__dissoc] !== undefined ? ret[IMap__dissoc](ret, k) : dissoc(ret, k);
    }
    return ret;
  }
  if (tc === MAP_TYPE) {
    // resolve each key to the representation the Map actually holds,
    // consistent with get/contains? (a keyword equals its name string)
    const keyIn = (mm, k) => {
      if (mm.has(k)) return k;
      const a = altKey(k);
      return a !== undefined && mm.has(a) ? a : undefined;
    };
    let present = false;
    for (const k of ks) if (keyIn(m, k) !== undefined) { present = true; break; }
    if (!present) return m;
    const m2 = copy(m);
    for (const k of ks) {
      const kk = keyIn(m2, k);
      if (kk !== undefined) m2.delete(kk);
    }
    return m2;
  }
  let present = false;
  for (const k of ks) if (k in m) { present = true; break; }
  if (!present) return m;
  const m2 = copy(m);
  for (const k of ks) delete m2[k];
  return m2;
}

export function inc(n) {
  return n + 1;
}

export function dec(n) {
  return n - 1;
}

export const _STAR_print_newline_STAR_ = { val: false };
export const _STAR_print_fn_STAR_ = { val: (s) => console.log(s) };
export const _STAR_print_err_fn_STAR_ = { val: (s) => console.error(s) };

export function print(...args) {
  _STAR_print_fn_STAR_.val(args.map((v) => toEDN(v, undefined, false)).join(' '));
}

export function println(...args) {
  print(...args);
  if (_STAR_print_newline_STAR_.val) _STAR_print_fn_STAR_.val('\n');
}

export function print_str(...args) {
  return args.map((v) => toEDN(v, undefined, false)).join(' ');
}

export function println_str(...args) {
  return print_str(...args) + '\n';
}

export function pr(...xs) {
  _STAR_print_fn_STAR_.val(pr_str(...xs));
}

export function nth(coll, idx, orElse) {
  if (typeof idx !== 'number') {
    throw new Error('Index argument to nth must be a number');
  }
  const hasDefault = arguments.length > 2;
  // nil coll puns to nil, like Clojure
  if (coll == null) return hasDefault ? orElse : null;
  // "found" is decided by the index bound, not the value. An in-bounds element
  // that happens to be undefined is still found.
  if (Array.isArray(coll)) {
    if (idx >= 0 && idx < coll.length) {
      return coll[idx];
    }
  } else if (coll[IIndexed__nth] !== undefined) {
    return hasDefault ? coll[IIndexed__nth](coll, idx, orElse) : coll[IIndexed__nth](coll, idx);
  } else if (idx >= 0) {
    // non-array: skip whole chunks instead of counting elements (handles
    // infinite seqs since it stops once idx is reached)
    const next = chunkCursor(coll);
    let base = 0;
    let ch;
    while ((ch = next()) !== null) {
      if (idx < base + ch.length) return ch[idx - base];
      base += ch.length;
    }
  }
  // out of bounds. With a default return it, otherwise throw like Clojure
  if (hasDefault) return orElse;
  throw new Error('Index out of bounds: ' + idx);
}

export function get(coll, key, otherwise = undefined) {
  if (coll == null) {
    return otherwise;
  }
  let v;
  // optimize for getting values out of objects
  if (isObj(coll)) {
    v = coll[key];
    if (v === undefined) {
      return otherwise;
    } else {
      return v;
    }
  }
  let g;
  switch (typeConst(coll)) {
    case SET_TYPE: {
      if (coll.has(key)) v = key;
      else {
        const a = altKey(key);
        if (a !== undefined && coll.has(a)) v = a;
      }
      break;
    }
    case MAP_TYPE: {
      v = coll.get(key);
      if (v === undefined) {
        const a = altKey(key);
        if (a !== undefined) v = coll.get(a);
      }
      break;
    }
    case ARRAY_TYPE:
      v = coll[key];
      break;
    default:
      if (coll[ILookup__lookup] !== undefined) {
        v = coll[ILookup__lookup](coll, key, otherwise);
        return v === undefined ? otherwise : v;
      }
      // we choose .get as the default implementation, e.g. fetch Headers are not Maps, but do implement a .get method
      g = coll['get'];
      if (typeof g === 'function') {
        try {
          v = coll.get(key);
          break;
        } catch (e) {
          // ignore error
        }
      }
      v = coll[key];
      break;
  }
  return v !== undefined ? v : otherwise;
}

export function seq_QMARK_(x) {
  return x != null && !!x[Symbol.iterator];
}

export function sequential_QMARK_(x) {
  // vectors and lists are arrays; lazy seqs and cons carry the lazy brand.
  // Sets, maps and strings are iterable but not sequential.
  return Array.isArray(x) || x?.[TYPE_TAG] === LAZY_ITERABLE_TYPE || (x != null && x[IVector.__sym] !== undefined);
}

export function seqable_QMARK_(x) {
  return (
    x === null ||
    x === undefined ||
    // plain objects (squint maps) are seqable via Object.entries in `iterable`,
    // even though they lack Symbol.iterator.
    object_QMARK_(x) ||
    // we used to check instanceof Object but this returns false for TC39 Records
    // also we used to write `Symbol.iterator in` but this does not work for strings and some other types
    !!x[Symbol.iterator] ||
    !!x[ISEQABLE_SYM]
  );
}

// squint has no distinct MapEntry type (map entries are plain 2-element
// arrays). We tag entries produced from a map with this marker symbol so
// map-entry? can tell them apart from ordinary vectors. Symbol-keyed props are
// invisible to =, into, iteration and JSON, so the effect is contained.
const MAP_ENTRY = Symbol('squint.lang.map-entry');

function tagMapEntry(e) {
  e[MAP_ENTRY] = true;
  return e;
}

export function map_entry_QMARK_(x) {
  return Array.isArray(x) && x[MAP_ENTRY] === true;
}

export function iterable(x) {
  // nil puns to empty iterable, support passing nil to first/rest/reduce, etc.
  if (x === null || x === undefined) {
    return [];
  }
  // fast path: anything with Symbol.iterator (arrays, strings, sets, maps,
  // lazy seqs). Inlined rather than calling seqable?, which also reports plain
  // objects as seqable; those are handled by the Object.entries branch below.
  if (x[Symbol.iterator]) {
    return x;
  }
  // a type extended to ISeqable seqs through its -seq method
  if (x[ISeqable__seq] !== undefined) return iterable(x[ISeqable__seq](x));
  // only a plain object is a squint map; a class instance without a native
  // iterator or ISeqable is not iterable, matching seqable? and CLJS, and
  // never leaks its internal fields
  if (isObj(x)) return Object.entries(x).map(tagMapEntry);
  throw new TypeError(`${x} is not iterable`);
}

export const IIterable = Symbol('Iterable');

export const IIterable__iterator = Symbol.iterator;

export function _iterator(coll) {
  return coll[Symbol.iterator]();
}

export const es6_iterator = _iterator;

export function seq(x) {
  if (x == null) return x;
  if (!seqable_QMARK_(x)) throw new TypeError(x + ' is not ISeqable');
  // a string seqs into its characters, like CLJS.
  if (typeof x === 'string') return x.length ? [...x] : null;
  const iter = iterable(x);
  // return nil for terminal checking
  if (iter.length === 0 || iter.size === 0) {
    return null;
  }
  // a set or map is iterable but not a sequence; materialize its entries
  // into a distinct seq so the result is not itself a set or map, like CLJS
  if (iter instanceof Set || iter[TYPE_TAG] === SET_TYPE) {
    return [...iter];
  }
  if (iter instanceof Map || iter[TYPE_TAG] === MAP_TYPE) {
    return [...iter].map(tagMapEntry);
  }
  // an instance with -dissoc is a map rep: same distinct entry seq
  if (iter[IMap__dissoc] !== undefined) {
    const entries = [...iter].map(tagMapEntry);
    return entries.length === 0 ? null : entries;
  }
  const _i = iter[Symbol.iterator]();
  if (_i.next().done) return null;
  return iter;
}

export function first(coll) {
  if (coll == null) return undefined;
  if (Array.isArray(coll)) return coll[0];
  if (coll instanceof LazyIterable) {
    coll.force();
    return coll.chunk === null ? undefined : coll.chunk[0];
  }
  // destructuring uses iterable protocol
  const [first] = iterable(coll);
  return first;
}

export function second(coll) {
  if (coll instanceof LazyIterable) {
    coll.force();
    const ch = coll.chunk;
    if (ch === null) return undefined;
    return ch.length > 1 ? ch[1] : first(coll._rest);
  }
  const [_, v] = iterable(coll);
  return v;
}

export function ffirst(coll) {
  return first(first(coll));
}

export function fnext(coll) {
  return first(next(coll));
}

export function nfirst(coll) {
  return next(first(coll));
}

export function rest(coll) {
  // chunk-aware: drop the first element of the first chunk, keep the rest of
  // the chain chunked (preserves chunkedness, unlike re-iterating element-wise)
  const cell = chunkCells(coll);
  cell.force();
  const ch = cell.chunk;
  if (ch === null) return cell; // (rest ()) is ()
  if (ch.length > 1) {
    const c = new LazyIterable(null);
    c.realized = true;
    c.chunk = ch.slice(1);
    c._rest = cell._rest;
    return c;
  }
  return cell._rest; // first chunk had one element; the next cell is the rest
}

const REDUCED_DEREF = (self) => self.value;

class Reduced {
  value;
  constructor(x) {
    this.value = x;
    this[IDeref__deref] = REDUCED_DEREF;
  }
}

export function last(coll) {
  coll = iterable(coll);
  if (Array.isArray(coll)) {
    return coll[coll.length - 1];
  }
  // non-array: walk chunks, keep the last chunk's last element
  const next = chunkCursor(coll);
  let lastEl;
  let ch;
  while ((ch = next()) !== null) lastEl = ch[ch.length - 1];
  return lastEl;
}

export function reduced(x) {
  return new Reduced(x);
}

export function reduced_QMARK_(x) {
  return x instanceof Reduced;
}

export function reduce(f, arg1, arg2) {
  f = __toFn(f);
  const hasInit = arguments.length !== 2;
  const coll = hasInit ? arg2 : arg1;
  let val = hasInit ? arg1 : undefined;

  // fast path: index loop over an array
  if (Array.isArray(coll)) {
    let i = 0;
    if (!hasInit) {
      if (coll.length === 0) return f();
      val = coll[0];
      i = 1;
    }
    if (val instanceof Reduced) return val.value;
    for (; i < coll.length; i++) {
      val = f(val, coll[i]);
      if (val instanceof Reduced) return val.value;
    }
    return val;
  }

  // non-array: walk chunks (chunked cell or any other seqable)
  const next = chunkCursor(coll);
  let ch = next();
  let i = 0;
  if (!hasInit) {
    if (ch === null) return f();
    val = ch[0];
    i = 1;
  }
  if (val instanceof Reduced) return val.value;
  while (ch !== null) {
    for (; i < ch.length; i++) {
      val = f(val, ch[i]);
      if (val instanceof Reduced) return val.value;
    }
    ch = next();
    i = 0;
  }
  return val;
}

function* _reductions2(f, s) {
  const vd = s.next();
  if (vd.done) {
    yield f();
  } else {
    yield* _reductions3(f, vd.value, s);
  }
}

function* _reductions3(f, init, coll) {
  let i = init;
  const rst = coll;
  while (true) {
    if (reduced_QMARK_(i)) {
      yield i.value;
      return;
    } else yield i;
    const vd = rst.next();
    if (vd.done) {
      break;
    }
    i = f(i, vd.value);
  }
}

export function reductions(f, arg1, arg2) {
  f = __toFn(f);
  if (arguments.length === 2) {
    const it = es6_iterator(iterable(arg1));
    return lazy(function* () {
      yield* _reductions2(f, it);
    });
  }
  const it = es6_iterator(iterable(arg2));
  return lazy(function* () {
    yield* _reductions3(f, arg1, it);
  });
}

// Deprecated: lazy values are now cached, so reuse no longer recomputes. Kept
// for API compatibility; remove in a future release.
export function warn_on_lazy_reusage_BANG_() {
  console.warn(
    'warn-on-lazy-reusage! is deprecated and does nothing: lazy values are now cached.',
  );
}

const CHUNK_SIZE = 32;

// One cell of a self-caching chunked seq. `step` is a thunk returning
// [nonEmptyChunkArray, nextStep] or null at the end. See doc/dev/lazy-seqs.md.
const LazyIterable = defclass(
class LazyIterable {
  constructor(step) {
    this[TYPE_TAG] = LAZY_ITERABLE_TYPE;
    this[IIterable] = true; // Closure compatibility
    this.step = step;
    this.realized = false;
    this.chunk = null; // array, or null when this is the terminal (empty) cell
    this._rest = null;
  }
  force() {
    if (!this.realized) {
      this.realized = true;
      const r = this.step();
      this.step = null;
      if (r !== null && r !== undefined) {
        this.chunk = r[0];
        this._rest = new LazyIterable(r[1]);
      }
    }
    return this;
  }
  [Symbol.iterator]() {
    let cell = this;
    let i = 0;
    return {
      next() {
        for (;;) {
          cell.force();
          const ch = cell.chunk;
          if (ch === null) return { value: undefined, done: true };
          if (i < ch.length) return { value: ch[i++], done: false };
          cell = cell._rest;
          i = 0;
        }
      },
      [Symbol.iterator]() {
        return this;
      },
    };
  }
  // Mirrors Array.prototype.indexOf so lazy seqs support (.indexOf coll x):
  // reference equality, returns -1 when absent. Unlike cljs.core, not by value.
  indexOf(x, fromIndex = 0) {
    let i = 0;
    for (const v of this) {
      if (i >= fromIndex && v === x) return i;
      i++;
    }
    return -1;
  }
}
);

// One-element chunks: an unchunked seq, realized one element at a time.
function unchunkedSteps(iter) {
  const step = () => {
    const r = iter.next();
    return r.done ? null : [[r.value], step];
  };
  return step;
}

// f is a zero-arg generator function; its result is an unchunked lazy seq.
export function lazy(f) {
  return new LazyIterable(unchunkedSteps(f()));
}

// gen(it) over a hoisted iterator: keeps the input head unpinned (streaming)
function lazyIter(coll, gen) {
  const it = es6_iterator(iterable(coll));
  return lazy(() => gen(it));
}

// A chunked view of any seqable for chunk-aware ops, preserving chunkedness:
// cells pass through, arrays slice into CHUNK_SIZE batches, others stay unchunked.
function chunkCells(coll) {
  if (coll instanceof LazyIterable) return coll;
  if (Array.isArray(coll)) {
    const step = (pos) => () => {
      if (pos >= coll.length) return null;
      const end = Math.min(pos + CHUNK_SIZE, coll.length);
      return [coll.slice(pos, end), step(end)];
    };
    return new LazyIterable(step(0));
  }
  return new LazyIterable(unchunkedSteps(es6_iterator(iterable(coll))));
}

// A cursor over a seq's chunks for realizers: returns a function yielding the
// next chunk array or null. Drive it with an inline loop (keeps an accumulator a
// plain local, unlike a callback). Callers keep their own array shortcut.
function chunkCursor(coll) {
  if (coll instanceof LazyIterable) {
    let cell = coll;
    return () => {
      if (cell === null) return null;
      cell.force();
      const ch = cell.chunk;
      cell = ch === null ? null : cell._rest;
      return ch;
    };
  }
  const it = es6_iterator(iterable(coll));
  return () => {
    const b = [];
    for (let i = 0; i < CHUNK_SIZE; i++) {
      const r = it.next();
      if (r.done) break;
      b.push(r.value);
    }
    return b.length === 0 ? null : b;
  };
}

// Build a lazy seq transforming each input chunk via xf(chunk, baseIndex) -> new
// chunk array, preserving chunkedness. Empty results are skipped (e.g. filter).
// baseIndex is the input element count before the chunk, for indexed ops.
function mapChunks(coll, xf) {
  const src = chunkCells(coll);
  const step = (cell, base) => () => {
    let c = cell;
    let b = base;
    for (;;) {
      c.force();
      const ch = c.chunk;
      if (ch === null) return null;
      const out = xf(ch, b);
      const rest = c._rest;
      b += ch.length;
      if (out.length !== 0) return [out, step(rest, b)];
      c = rest;
    }
  };
  return new LazyIterable(step(src, 0));
}

export const Cons = defclass(
class Cons {
  constructor(x, coll) {
    this[TYPE_TAG] = LAZY_ITERABLE_TYPE;
    this.x = x;
    this.coll = coll;
  }
  [Symbol.iterator]() {
    const x = this.x;
    let coll = this.coll;
    let started = false;
    let it = null;
    return {
      next() {
        if (!started) {
          started = true;
          return { value: x, done: false };
        }
        if (!it) {
          it = es6_iterator(iterable(coll));
          coll = null; // release the tail head so a single pass streams
        }
        return it.next();
      },
      [Symbol.iterator]() {
        return this;
      },
    };
  }
}
);

export function cons(x, coll) {
  // like CLJS cons, which seqs a non-ISeq tail
  if (!seqable_QMARK_(coll)) throw new TypeError(coll + ' is not ISeqable');
  return new Cons(x, coll);
}

export function map(f, ...colls) {
  f = __toFn(f);
  switch (colls.length) {
    case 0:
      return (rf) => {
        return (...args) => {
          switch (args.length) {
            case 0: {
              return rf();
            }
            case 1: {
              return rf(args[0]);
            }
            case 2: {
              return rf(args[0], f(args[1]));
            }
            default: {
              return rf(args[0], f(...args.slice(1)));
            }
          }
        };
      };
    case 1:
      return mapChunks(colls[0], (ch) => {
        const out = new Array(ch.length);
        for (let i = 0; i < ch.length; i++) out[i] = f(ch[i]);
        return out;
      });
    default: {
      const iters = colls.map((coll) => es6_iterator(iterable(coll)));
      return lazy(function* () {
        while (true) {
          const args = [];
          for (const i of iters) {
            const nextVal = i.next();
            if (nextVal.done) {
              return;
            }
            args.push(nextVal.value);
          }
          yield f(...args);
        }
      });
    }
  }
}

// 0/1 arities pass through to rf; step(rf) is the 2-arity reducer
function transducer(step) {
  return (rf) => {
    const s = step(rf);
    return (...args) =>
      args.length === 0 ? rf() : args.length === 1 ? rf(args[0]) : s(args[0], args[1]);
  };
}

function filter1(pred) {
  return transducer((rf) => (r, x) => (truth_(pred(x)) ? rf(r, x) : r));
}

export function filter(pred, coll) {
  if (arguments.length === 1) {
    return filter1(pred);
  }
  pred = __toFn(pred);
  return mapChunks(coll, (ch) => {
    const out = [];
    for (let i = 0; i < ch.length; i++) {
      const x = ch[i];
      if (truth_(pred(x))) out.push(x);
    }
    return out;
  });
}

export function filterv(pred, coll) {
  // filter is chunked; vec bulk-appends its chunks
  return pushAll([], filter(pred, coll));
}

export function random_sample(prob, coll) {
  if (arguments.length === 1) {
    return filter((_) => rand() < prob);
  }
  return filter((_) => rand() < prob, coll);
}

export function remove(pred, coll) {
  if (arguments.length === 1) {
    return filter1(complement(pred));
  }
  return filter(complement(pred), coll);
}

function map_indexed1(f) {
  return transducer((rf) => {
    let i = -1;
    return (r, x) => rf(r, f(++i, x));
  });
}

export function map_indexed(f, coll) {
  f = __toFn(f);
  if (arguments.length === 1) {
    return map_indexed1(f);
  }
  return mapChunks(coll, (ch, base) => {
    const out = new Array(ch.length);
    for (let i = 0; i < ch.length; i++) out[i] = f(base + i, ch[i]);
    return out;
  });
}

function keep_indexed2(f, coll) {
  f = __toFn(f);
  return mapChunks(coll, (ch, base) => {
    const out = [];
    for (let i = 0; i < ch.length; i++) {
      const v = f(base + i, ch[i]);
      if (truth_(v)) out.push(v);
    }
    return out;
  });
}

function keep_indexed1(f) {
  return transducer((rf) => {
    let ia = -1;
    return (r, x) => {
      const v = f(++ia, x);
      return v == null ? r : rf(r, v);
    };
  });
}

export function keep_indexed(f, coll) {
  if (arguments.length === 1) {
    return keep_indexed1(f);
  } else {
    return keep_indexed2(f, coll);
  }
}

export function str(...xs) {
  return xs.join('');
}

export function name(x) {
  if (isKw(x)) x = String(x);
  if (typeof x === 'string') {
    // keywords/symbols are strings in squint; name is the part after the "/"
    // ns separator (consistent with `namespace`, which returns the part before)
    const i = x.indexOf('/');
    return i >= 1 ? x.slice(i + 1) : x;
  }
  throw new Error("Doesn't support name: " + typeof x);
}

export function not(expr) {
  return !truth_(expr);
}

export function nil_QMARK_(v) {
  return v == null;
}

export const PROTOCOL_SENTINEL = {};

// marker protocols so (satisfies? IAtom x) works, like CLJS. Marked in the
// constructor, not on the prototype, so no top-level mutation pins Atom
// into bundles that do not use it.
const IATOM_SYM = Symbol('squint.core.IAtom');
const IDEREF_SYM = Symbol('squint.core.IDeref');
const ISEQABLE_SYM = Symbol('squint.core.ISeqable');
export const IAtom = { __sym: IATOM_SYM };
export const IDeref = { __sym: IDEREF_SYM };
// method slot for (-deref x), named like the defprotocol emission so
// (extend-type T IDeref (-deref [x] ...)) fills it
export const IDeref__deref = Symbol('IDeref_-deref');
export function _deref(o) {
  if (o != null && o[IDeref__deref] !== undefined) return o[IDeref__deref](o);
  return nilImpl(_deref, 'IDeref.-deref', o)(o);
}
export const ISeqable = { __sym: ISEQABLE_SYM };
export const ISeqable__seq = Symbol('ISeqable_-seq');

// map-facing protocols. Each dispatches through its slot in the extension
// path (INSTANCE_TYPE) of the corresponding core fn, so plain objects and
// arrays never pay for them. Slot symbols are separate consts so a bundle
// using only e.g. conj pulls one symbol, not the whole protocol set.
export const ILookup = { __sym: Symbol('squint.core.ILookup') };
export const ILookup__lookup = Symbol('ILookup_-lookup');
export const IAssociative = { __sym: Symbol('squint.core.IAssociative') };
export const IAssociative__assoc = Symbol('IAssociative_-assoc');
export const IAssociative__contains_key_QMARK_ = Symbol('IAssociative_-contains-key?');
export const IMap = { __sym: Symbol('squint.core.IMap') };
export const IMap__dissoc = Symbol('IMap_-dissoc');
export const ICounted = { __sym: Symbol('squint.core.ICounted') };
export const ICounted__count = Symbol('ICounted_-count');
export const IKVReduce = { __sym: Symbol('squint.core.IKVReduce') };
export const IKVReduce__kv_reduce = Symbol('IKVReduce_-kv-reduce');
export const ICollection = { __sym: Symbol('squint.core.ICollection') };
export const ICollection__conj = Symbol('ICollection_-conj');
export const IEmptyableCollection = { __sym: Symbol('squint.core.IEmptyableCollection') };
export const IEmptyableCollection__empty = Symbol('IEmptyableCollection_-empty');
export const IEquiv = { __sym: Symbol('squint.core.IEquiv') };
export const IEquiv__equiv = Symbol('IEquiv_-equiv');
// set and transient protocols, same extension-path dispatch as the map-facing
// protocols above
export const ISet = { __sym: Symbol('squint.core.ISet') };
export const ISet__disjoin = Symbol('ISet_-disjoin');
export const IEditableCollection = { __sym: Symbol('squint.core.IEditableCollection') };
export const IEditableCollection__as_transient = Symbol('IEditableCollection_-as-transient');
export const ITransientCollection = { __sym: Symbol('squint.core.ITransientCollection') };
export const ITransientCollection__conj_BANG_ = Symbol('ITransientCollection_-conj!');
export const ITransientCollection__persistent_BANG_ = Symbol('ITransientCollection_-persistent!');
export const ITransientAssociative = { __sym: Symbol('squint.core.ITransientAssociative') };
export const ITransientAssociative__assoc_BANG_ = Symbol('ITransientAssociative_-assoc!');
export const ITransientMap = { __sym: Symbol('squint.core.ITransientMap') };
export const ITransientMap__dissoc_BANG_ = Symbol('ITransientMap_-dissoc!');
export const ITransientSet = { __sym: Symbol('squint.core.ITransientSet') };
export const ITransientSet__disjoin_BANG_ = Symbol('ITransientSet_-disjoin!');
// metadata protocols, like CLJS: types implement the slots, plain values
// get instance-level impls installed by with-meta
export const IMeta = { __sym: Symbol('squint.core.IMeta') };
export const IMeta__meta = Symbol('IMeta_-meta');
export const IWithMeta = { __sym: Symbol('squint.core.IWithMeta') };
export const IWithMeta__with_meta = Symbol('IWithMeta_-with-meta');
// hashing (Murmur3, like CLJS). The contract: (= a b) implies (hash a) ===
// (hash b), where = is dequal. None of this is referenced by =, so bundles
// that only compare pay nothing.
// Ported from ClojureScript (cljs/core.cljs), Copyright (c) Rich Hickey and
// contributors, Eclipse Public License 1.0. MurmurHash3 by Austin Appleby
// (public domain).

// IHash: a custom type opts into value hashing
export const IHash = { __sym: Symbol('squint.core.IHash') };
export const IHash__hash = Symbol('IHash_-hash');

// the equality hashed collections key by: identical or -equiv, never a
// deep compare like =
export function equiv(x, y) {
  if (x === y) return true;
  if (x == null) return y == null;
  if (y == null) return false;
  if (x[IEquiv__equiv] !== undefined) return !!x[IEquiv__equiv](x, y);
  if (y[IEquiv__equiv] !== undefined) return !!y[IEquiv__equiv](y, x);
  if (x instanceof Date && y instanceof Date) return x.getTime() === y.getTime();
  return false;
}

// identity uid for reference-keyed values, like CLJS goog/getUid
const UIDS = /* @__PURE__ */ new WeakMap();
let uidCounter = 0;

function uid(o) {
  let id = UIDS.get(o);
  if (id === undefined) {
    id = ++uidCounter;
    UIDS.set(o, id);
  }
  return id;
}

const imul = Math.imul;
const M3_C1 = 0xcc9e2d51 | 0;
const M3_C2 = 0x1b873593 | 0;

function rotl(x, n) {
  return (x << n) | (x >>> (32 - n));
}

function m3MixK1(k1) {
  return imul(rotl(imul(k1 | 0, M3_C1), 15), M3_C2);
}

function m3MixH1(h1, k1) {
  return (imul(rotl((h1 | 0) ^ (k1 | 0), 13), 5) + (0xe6546b64 | 0)) | 0;
}

function m3Fmix(h1, len) {
  h1 = (h1 ^ len) | 0;
  h1 = (h1 ^ (h1 >>> 16)) | 0;
  h1 = imul(h1, 0x85ebca6b | 0);
  h1 = (h1 ^ (h1 >>> 13)) | 0;
  h1 = imul(h1, 0xc2b2ae35 | 0);
  return (h1 ^ (h1 >>> 16)) | 0;
}

function m3HashInt(x) {
  return x === 0 ? 0 : m3Fmix(m3MixH1(0, m3MixK1(x)), 4);
}

let stringHashCache = /* @__PURE__ */ Object.create(null);
let stringHashCacheCount = 0;

function hashStringRaw(s) {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (imul(31, h) + s.charCodeAt(i)) | 0;
  return h;
}

function hashString(s) {
  if (stringHashCacheCount > 8192) {
    stringHashCache = Object.create(null);
    stringHashCacheCount = 0;
  }
  let h = stringHashCache[s];
  if (typeof h !== 'number') {
    h = hashStringRaw(s);
    stringHashCache[s] = h;
    stringHashCacheCount++;
  }
  return h;
}

// one IIFE, not two consts: `new Int32Array(F64.buffer)` reads a property,
// which pins F64 into bundles even under a pure annotation
const HASH_BUF = /* @__PURE__ */ (() => {
  const f64 = new Float64Array(1);
  return { f64, i32: new Int32Array(f64.buffer) };
})();

function hashDouble(n) {
  HASH_BUF.f64[0] = n;
  return (HASH_BUF.i32[0] ^ HASH_BUF.i32[1]) | 0;
}

function mixCollectionHash(hashBasis, count) {
  return m3Fmix(m3MixH1(0, m3MixK1(hashBasis)), count);
}

export function hash_ordered_coll(coll) {
  let n = 0;
  let h = 1;
  for (const x of coll) {
    h = (imul(31, h) + hash(x)) | 0;
    n++;
  }
  return mixCollectionHash(h, n);
}

export function hash_unordered_coll(coll) {
  let n = 0;
  let h = 0;
  for (const x of coll) {
    h = (h + hash(x)) | 0;
    n++;
  }
  return mixCollectionHash(h, n);
}

// a map entry hashes as (hash-ordered-coll [k v])
function hashEntry(k, v) {
  return mixCollectionHash((imul(31, (imul(31, 1) + hash(k)) | 0) + hash(v)) | 0, 2);
}

function hashMapEntries(entries) {
  let n = 0;
  let h = 0;
  for (const [k, v] of entries) {
    h = (h + hashEntry(k, v)) | 0;
    n++;
  }
  return mixCollectionHash(h, n);
}

// (equiv a b) implies (hash a) === (hash b): plain mutable data hashes by
// uid, a type with -equiv but no -hash gets a structural entry hash
export function hash(o) {
  if (o == null) return 0;
  switch (typeof o) {
    case 'number':
      if (Number.isFinite(o)) {
        return Number.isSafeInteger(o) ? o % 2147483647 | 0 : hashDouble(o);
      }
      if (o === Infinity) return 2146435072;
      if (o === -Infinity) return -1048576;
      return 2146959360; // NaN
    case 'boolean':
      return o ? 1231 : 1237;
    case 'string':
      return m3HashInt(hashString(o));
    case 'bigint':
      return Number(o % 2147483647n) | 0;
    case 'function':
      return uid(o) | 0;
    case 'object':
      break;
    default:
      // JS symbols compare by identity; a constant hash is consistent
      return 0;
  }
  if (o[IHash__hash] !== undefined) return o[IHash__hash](o) | 0;
  if (o instanceof Date) return o.valueOf() | 0;
  if (o[IEquiv__equiv] !== undefined) return hashMapEntries(Object.entries(o));
  return uid(o) | 0;
}

// printing protocols, like CLJS: a type prints itself through
// (-pr-writer [obj writer opts]), writing strings via (-write writer s)
export const IWriter = { __sym: Symbol('squint.core.IWriter') };
export const IWriter__write = Symbol('IWriter_-write');
export const IPrintWithWriter = { __sym: Symbol('squint.core.IPrintWithWriter') };
export const IPrintWithWriter__pr_writer = Symbol('IPrintWithWriter_-pr-writer');
export function _write(writer, s) {
  if (writer != null && writer[IWriter__write] !== undefined) return writer[IWriter__write](writer, s);
  return nilImpl(_write, 'IWriter.-write', writer)(writer, s);
}
export function write_all(writer, ...ss) {
  for (const s of ss) _write(writer, s);
}
// vector-facing protocols, dispatched like the map-facing set above
export const IStack = { __sym: Symbol('squint.core.IStack') };
export const IStack__peek = Symbol('IStack_-peek');
export const IStack__pop = Symbol('IStack_-pop');
export const IIndexed = { __sym: Symbol('squint.core.IIndexed') };
export const IIndexed__nth = Symbol('IIndexed_-nth');
export const ITransientVector = { __sym: Symbol('squint.core.ITransientVector') };
export const ITransientVector__pop_BANG_ = Symbol('ITransientVector_-pop!');
// marker protocol: a non-array type that counts as a vector (vector?,
// sequential?, vec, subvec route through it)
export const IVector = { __sym: Symbol('squint.core.IVector') };
// a type converts itself in clj->js through this slot, like CLJS IEncodeJS;
// the impl receives (x, recur) with recur a cycle-safe clj->js
export const IEncodeJS = { __sym: Symbol('squint.core.IEncodeJS') };
export const IEncodeJS__clj__GT_js = Symbol('IEncodeJS_-clj->js');
// marker protocol set by defrecord
export const IRecord = { __sym: Symbol('squint.core.IRecord') };

export function record_QMARK_(x) {
  return x != null && x[IRecord.__sym] !== undefined;
}

// The protocol-method fns below share one shape but stay hand-written on
// purpose: a shared factory makes V8 share type feedback across all of them,
// turning every slot access megamorphic (measured 1.8 -> 11 ns per call).
export function _lookup(o, k, nf) {
  if (o != null && o[ILookup__lookup] !== undefined) return o[ILookup__lookup](o, k, nf);
  return nilImpl(_lookup, 'ILookup.-lookup', o)(o, k, nf);
}
export function _assoc(o, k, v) {
  if (o != null && o[IAssociative__assoc] !== undefined) return o[IAssociative__assoc](o, k, v);
  return nilImpl(_assoc, 'IAssociative.-assoc', o)(o, k, v);
}
export function _contains_key_QMARK_(o, k) {
  if (o != null && o[IAssociative__contains_key_QMARK_] !== undefined) return o[IAssociative__contains_key_QMARK_](o, k);
  return nilImpl(_contains_key_QMARK_, 'IAssociative.-contains-key?', o)(o, k);
}
export function _dissoc(o, k) {
  if (o != null && o[IMap__dissoc] !== undefined) return o[IMap__dissoc](o, k);
  return nilImpl(_dissoc, 'IMap.-dissoc', o)(o, k);
}
export function _count(o) {
  if (o != null && o[ICounted__count] !== undefined) return o[ICounted__count](o);
  return nilImpl(_count, 'ICounted.-count', o)(o);
}
export function _kv_reduce(o, f, init) {
  if (o != null && o[IKVReduce__kv_reduce] !== undefined) return o[IKVReduce__kv_reduce](o, f, init);
  return nilImpl(_kv_reduce, 'IKVReduce.-kv-reduce', o)(o, f, init);
}
export function _conj(o, x) {
  if (o != null && o[ICollection__conj] !== undefined) return o[ICollection__conj](o, x);
  return nilImpl(_conj, 'ICollection.-conj', o)(o, x);
}
export function _empty(o) {
  if (o != null && o[IEmptyableCollection__empty] !== undefined) return o[IEmptyableCollection__empty](o);
  return nilImpl(_empty, 'IEmptyableCollection.-empty', o)(o);
}
export function _equiv(o, other) {
  if (o != null && o[IEquiv__equiv] !== undefined) return o[IEquiv__equiv](o, other);
  return nilImpl(_equiv, 'IEquiv.-equiv', o)(o, other);
}
export function _seq(o) {
  if (o != null && o[ISeqable__seq] !== undefined) return o[ISeqable__seq](o);
  return nilImpl(_seq, 'ISeqable.-seq', o)(o);
}
export function _disjoin(o, x) {
  if (o != null && o[ISet__disjoin] !== undefined) return o[ISet__disjoin](o, x);
  return nilImpl(_disjoin, 'ISet.-disjoin', o)(o, x);
}
export function _as_transient(o) {
  if (o != null && o[IEditableCollection__as_transient] !== undefined) return o[IEditableCollection__as_transient](o);
  return nilImpl(_as_transient, 'IEditableCollection.-as-transient', o)(o);
}
export function _conj_BANG_(o, x) {
  if (o != null && o[ITransientCollection__conj_BANG_] !== undefined) return o[ITransientCollection__conj_BANG_](o, x);
  return nilImpl(_conj_BANG_, 'ITransientCollection.-conj!', o)(o, x);
}
export function _persistent_BANG_(o) {
  if (o != null && o[ITransientCollection__persistent_BANG_] !== undefined) return o[ITransientCollection__persistent_BANG_](o);
  return nilImpl(_persistent_BANG_, 'ITransientCollection.-persistent!', o)(o);
}
export function _assoc_BANG_(o, k, v) {
  if (o != null && o[ITransientAssociative__assoc_BANG_] !== undefined) return o[ITransientAssociative__assoc_BANG_](o, k, v);
  return nilImpl(_assoc_BANG_, 'ITransientAssociative.-assoc!', o)(o, k, v);
}
export function _dissoc_BANG_(o, k) {
  if (o != null && o[ITransientMap__dissoc_BANG_] !== undefined) return o[ITransientMap__dissoc_BANG_](o, k);
  return nilImpl(_dissoc_BANG_, 'ITransientMap.-dissoc!', o)(o, k);
}
export function _disjoin_BANG_(o, x) {
  if (o != null && o[ITransientSet__disjoin_BANG_] !== undefined) return o[ITransientSet__disjoin_BANG_](o, x);
  return nilImpl(_disjoin_BANG_, 'ITransientSet.-disjoin!', o)(o, x);
}

// an extend-type on nil stores the impl on the dispatch fn under null,
// the same convention the generated protocol dispatch fns use
function nilImpl(dispatchFn, protoMethod, o) {
  const f = dispatchFn[null];
  if (f === undefined) throw missing_protocol(protoMethod, o);
  return f;
}
export const IReset = { __sym: Symbol('squint.core.IReset') };
export const IReset__reset_BANG_ = Symbol('IReset_-reset!');
export function _reset_BANG_(o, v) {
  if (o != null && o[IReset__reset_BANG_] !== undefined) return o[IReset__reset_BANG_](o, v);
  return nilImpl(_reset_BANG_, 'IReset.-reset!', o)(o, v);
}
export const ISwap = { __sym: Symbol('squint.core.ISwap') };
export const ISwap__swap_BANG_ = Symbol('ISwap_-swap!');
export function _swap_BANG_(o, f, ...args) {
  if (o != null && o[ISwap__swap_BANG_] !== undefined) return o[ISwap__swap_BANG_](o, f, ...args);
  return nilImpl(_swap_BANG_, 'ISwap.-swap!', o)(o, f, ...args);
}
export const IWatchable = { __sym: Symbol('squint.core.IWatchable') };
export const IWatchable__add_watch = Symbol('IWatchable_-add-watch');
export const IWatchable__remove_watch = Symbol('IWatchable_-remove-watch');
export const IWatchable__notify_watches = Symbol('IWatchable_-notify-watches');
export function _add_watch(o, k, f) {
  if (o != null && o[IWatchable__add_watch] !== undefined) return o[IWatchable__add_watch](o, k, f);
  return nilImpl(_add_watch, 'IWatchable.-add-watch', o)(o, k, f);
}
export function _remove_watch(o, k) {
  if (o != null && o[IWatchable__remove_watch] !== undefined) return o[IWatchable__remove_watch](o, k);
  return nilImpl(_remove_watch, 'IWatchable.-remove-watch', o)(o, k);
}
export function _notify_watches(o, oldv, newv) {
  if (o != null && o[IWatchable__notify_watches] !== undefined) return o[IWatchable__notify_watches](o, oldv, newv);
  return nilImpl(_notify_watches, 'IWatchable.-notify-watches', o)(o, oldv, newv);
}

// shared protocol impls: one fn per operation, a pointer per instance
const ATOM_DEREF = (self) => self.val;
const ATOM_RESET = (self, x) => {
  if (self._validator && !truth_(self._validator(x))) {
    throw new Error('Validator rejected reference state');
  }
  const old_val = self.val;
  self.val = x;
  if (self._hasWatches) {
    for (const [k, f] of Object.entries(self._watches)) f(k, self, old_val, x);
  }
  return x;
};
const ATOM_SWAP = function (self, f, a, b, xs) {
  switch (arguments.length) {
    case 2:
      return ATOM_RESET(self, f(self.val));
    case 3:
      return ATOM_RESET(self, f(self.val, a));
    case 4:
      return ATOM_RESET(self, f(self.val, a, b));
    default:
      return ATOM_RESET(self, f(self.val, a, b, ...xs));
  }
};
const ATOM_ADD_WATCH = (self, k, f) => {
  self._watches[k] = f;
  self._hasWatches = true;
};
const ATOM_REMOVE_WATCH = (self, k) => {
  delete self._watches[k];
};
const ATOM_NOTIFY = (self, oldv, newv) => {
  for (const [k, f] of Object.entries(self._watches)) f(k, self, oldv, newv);
};

export class Atom {
  constructor(init) {
    this.val = init;
    this._watches = {};
    this._hasWatches = false;
    this[IATOM_SYM] = true;
    this[IDEREF_SYM] = true;
    this[IDeref__deref] = ATOM_DEREF;
    this[IReset.__sym] = true;
    this[IReset__reset_BANG_] = ATOM_RESET;
    this[ISwap.__sym] = true;
    this[ISwap__swap_BANG_] = ATOM_SWAP;
    this[IWatchable.__sym] = true;
    this[IWatchable__add_watch] = ATOM_ADD_WATCH;
    this[IWatchable__remove_watch] = ATOM_REMOVE_WATCH;
    this[IWatchable__notify_watches] = ATOM_NOTIFY;
  }
}

export function atom(init, ...opts) {
  const a = new Atom(init);
  for (let i = 0; i < opts.length; i += 2) {
    // IMeta only, like CLJS Atom: keeps with-meta's copy machinery out of atom-only bundles
    const opt = String(opts[i]);
    if (opt === 'meta') {
      const mv = opts[i + 1];
      a[IMeta__meta] = () => mv;
    }
    else if (opt === 'validator') a._validator = opts[i + 1];
  }
  return a;
}

export function get_validator(ref) {
  return ref._validator ?? null;
}

export function set_validator_BANG_(ref, f) {
  if (f != null && !truth_(f(deref(ref)))) {
    throw new Error('Validator rejected reference state');
  }
  ref._validator = f;
  return null;
}

// the CLJS missing-protocol error, used by every protocol dispatch miss
export function missing_protocol(proto, obj) {
  let ty;
  if (obj === null) ty = 'null';
  else if (obj === undefined) ty = 'undefined';
  else if (Array.isArray(obj)) ty = 'array';
  else if (typeof obj === 'object' && obj.constructor && obj.constructor !== Object) {
    ty = obj.constructor.name;
  } else ty = typeof obj;
  return new Error(
    `No protocol method ${proto} defined for type ${ty}: ${obj ?? ''}`
  );
}

export function deref(ref) {
  if (ref?.[IDeref__deref] !== undefined) return ref[IDeref__deref](ref);
  return nilImpl(_deref, 'IDeref.-deref', ref)(ref);
}

export function reset_BANG_(atm, v) {
  if (atm?.[IReset__reset_BANG_] !== undefined) return atm[IReset__reset_BANG_](atm, v);
  return nilImpl(_reset_BANG_, 'IReset.-reset!', atm)(atm, v);
}

export function swap_BANG_(atm, f, ...args) {
  f = __toFn(f);
  if (atm?.[ISwap__swap_BANG_] !== undefined) {
    // the CLJS -swap! contract: up to two positional args, the rest packed
    switch (args.length) {
      case 0:
        return atm[ISwap__swap_BANG_](atm, f);
      case 1:
        return atm[ISwap__swap_BANG_](atm, f, args[0]);
      case 2:
        return atm[ISwap__swap_BANG_](atm, f, args[0], args[1]);
      default:
        return atm[ISwap__swap_BANG_](atm, f, args[0], args[1], args.slice(2));
    }
  }
  const v = f(deref(atm), ...args);
  reset_BANG_(atm, v);
  return v;
}

export function swap_vals_BANG_(atm, f, ...args) {
  const oldv = deref(atm);
  f = __toFn(f);
  const newv = f(oldv, ...args);
  reset_BANG_(atm, newv);
  return [oldv, newv];
}

export function reset_vals_BANG_(atm, newv) {
  const oldv = deref(atm);
  reset_BANG_(atm, newv);
  return [oldv, newv];
}

export function compare_and_set_BANG_(atm, oldv, newv) {
  if (deref(atm) === oldv) {
    reset_BANG_(atm, newv);
    return true;
  } else {
    return false;
  }
}

export function range(begin, end, step) {
  // range is a chunked source: it realizes CHUNK_SIZE values at a time
  let b = begin,
    e = end,
    s = step;
  if (end === undefined) {
    b = 0;
    e = begin;
  }
  const start = b || 0;
  s = step ?? 1;
  const ascending = s >= 0;
  const more = (i) => e === undefined || (ascending && i < e) || (!ascending && e < i);
  const mkStep = (from) => () => {
    if (!more(from)) return null;
    const out = [];
    let i = from;
    while (out.length < CHUNK_SIZE && more(i)) {
      out.push(i);
      i += s;
    }
    return [out, mkStep(i)];
  };
  return new LazyIterable(mkStep(start));
}

export function re_matches(re, s) {
  const matches = re.exec(s);
  if (matches && s === matches[0]) {
    if (matches.length === 1) {
      return matches[0];
    } else {
      return matches;
    }
  }
  return null;
}

export function re_find(re, s) {
  if (string_QMARK_(s)) {
    const matches = re.exec(s);
    if (matches != null) {
      if (matches.length === 1) return matches[0];
      else {
        return [...matches];
      }
    }
    return null;
  } else {
    throw new TypeError('re-find must match against a string.');
  }
}

export function re_pattern(s) {
  if (s instanceof RegExp) {
    return s;
  }

  // Allow all flags available in JavaScript
  // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp/RegExp#flags
  const flagMatches = s.match(/^\(\?([dgimsuvy]*)\)/);

  if (flagMatches) {
    return new RegExp(s.slice(flagMatches[0].length), flagMatches[1]);
  }

  return new RegExp(s);
}

export function subvec(arr, start, end) {
  // an IVector type slices through its own slots (bounds-checked nth + conj)
  if (arr != null && arr[IVector.__sym] !== undefined) {
    if (end === undefined) end = _count(arr);
    if (start == null || end == null) {
      throw new Error('subvec: start and end must not be nil');
    }
    start = start | 0;
    end = end | 0;
    if (start < 0 || end < start || end > _count(arr)) {
      throw new Error('subvec: index out of bounds');
    }
    let ret = arr[IEmptyableCollection__empty](arr);
    for (let i = start; i < end; i++) ret = ret[ICollection__conj](ret, arr[IIndexed__nth](arr, i));
    return ret;
  }
  if (!isVectorArray(arr)) {
    throw new Error('subvec: argument must be a vector');
  }
  if (end === undefined) end = arr.length;
  if (start == null || end == null) {
    throw new Error('subvec: start and end must not be nil');
  }
  // CLJS coerces the indices with (int x) before bounds-checking.
  start = start | 0;
  end = end | 0;
  if (start < 0 || end < start || end > arr.length) {
    throw new Error('subvec: index out of bounds');
  }
  return arr.slice(start, end);
}

export function vector(...args) {
  return args;
}

export const array = vector;

export function vector_QMARK_(x) {
  if (x == null) return false;
  return isVectorArray(x) || x[IVector.__sym] !== undefined;
}

export function mapv(...args) {
  if (args.length === 2) {
    const [_f, coll] = args;
    const f = __toFn(_f);
    const iter = iterable(coll);
    if (Array.isArray(iter)) {
      // explicit for loop was faster
      const ret = new Array(iter.length);
      for (var i = 0; i < iter.length; i++) {
        ret[i] = f(iter[i]);
      }
      return ret;
    } else {
      // non-array: map whole chunks straight into the result array
      const ret = [];
      const next = chunkCursor(iter);
      let ch;
      while ((ch = next()) !== null) {
        for (let i = 0; i < ch.length; i++) ret.push(f(ch[i]));
      }
      return ret;
    }
  }
  return [...map(...args)];
}

// Append every element of `from` to array `out`, bulk-appending whole chunks
// for a chunked seq. Returns out.
function pushAll(out, from) {
  if (from instanceof LazyIterable) {
    let cell = from;
    for (;;) {
      cell.force();
      const ch = cell.chunk;
      if (ch === null) return out;
      Array.prototype.push.apply(out, ch);
      cell = cell._rest;
    }
  }
  for (const x of iterable(from)) out.push(x);
  return out;
}

// Materialize a seq into a fresh, mutable array (bulk-copies chunked seqs).
function toArray(coll) {
  if (coll instanceof LazyIterable) return pushAll([], coll);
  return [...iterable(coll)];
}

export function vec(x) {
  if (isVectorArray(x)) return x;
  if (x != null && x[IVector.__sym] !== undefined) return x;
  return pushAll([], x);
}

export function set(coll) {
  return new Set(iterable(coll));
}

export function set_QMARK_(x) {
  if (x == null) return false;
  return typeConst(x) === SET_TYPE || x[ISet.__sym] !== undefined;
}

export function hash_set(...xs) {
  return new Set(xs);
}

export function sorted_QMARK_(x) {
  return x != null && x[SORTED_TAG] === true;
}

export function char_QMARK_(x) {
  return typeof x === 'string' && x.length === 1;
}

export function char$(x) {
  if (typeof x === 'string' && x.length === 1) return x;
  if (typeof x === 'number') return String.fromCharCode(x);
  throw new Error('Argument to char must be a character or number: ' + x);
}

export function apply(f, ...args) {
  f = __toFn(f);
  const xs = args.slice(0, args.length - 1);
  const last = args[args.length - 1];
  // variadic impl hint; see doc/ai/adr/0001
  const v = f.squint$lang$variadic;
  if (v) {
    // pull maxfa fixed args (bounded, lazy-safe); more left -> variadic, else fixed
    const maxfa = v.length - 1;
    const fixed = [];
    let i = 0, rest;
    for (; i < maxfa && i < xs.length; i++) fixed.push(xs[i]);
    if (i < maxfa) {
      let s = seq(last);
      for (; i < maxfa && s != null; i++) {
        fixed.push(first(s));
        s = next(s);
      }
      rest = s;
    } else {
      rest = i < xs.length ? concat1([xs.slice(i), last]) : last;
    }
    rest = rest == null ? null : seq(rest);
    if (rest == null) return f(...fixed);
    return v(...fixed, rest);
  }
  return f(...xs, ...iterable(last));
}

export function even_QMARK_(x) {
  if (!Number.isInteger(x)) {
    throw new Error(`Argument must be an integer: ${x}`);
  }
  return x % 2 == 0;
}

export function odd_QMARK_(x) {
  return !even_QMARK_(x);
}

export function complement(f) {
  f = __toFn(f);
  return (...args) => not(f(...args));
}

export function constantly(x) {
  return (..._) => x;
}

class List extends Array {
  constructor(...args) {
    super();
    this[TYPE_TAG] = LIST_TYPE;
    this.push(...args);
  }
}

export function list_QMARK_(x) {
  return x?.[TYPE_TAG] === LIST_TYPE;
}

export function list(...args) {
  return new List(...args);
}

export function list_STAR_(...args) {
  const last = args.pop();
  return new List(...args, ...iterable(last));
}

export function array_QMARK_(x) {
  return Array.isArray(x);
}

const CONCAT_DONE = Symbol('concat-done');

function concat1(colls) {
  // chunk-aware: pass each coll's chunks through, preserving chunkedness. Each
  // coll head is released once consumed so a single pass streams.
  const isArr = Array.isArray(colls);
  const arr = isArr ? colls.slice() : null;
  const collIter = isArr ? null : es6_iterator(iterable(colls));
  let idx = 0;
  const nextColl = () => {
    if (isArr) {
      if (idx >= arr.length) return CONCAT_DONE;
      const c = arr[idx];
      arr[idx] = null;
      idx++;
      return c;
    }
    const r = collIter.next();
    return r.done ? CONCAT_DONE : r.value;
  };
  // src is null, a chunked cell, or {a, pos} for an array (raw-array fast path:
  // no per-coll wrapper). Continuations carry immutable state, so a single pass
  // streams.
  const step = (src) => () => {
    for (;;) {
      if (src !== null) {
        if (src instanceof LazyIterable) {
          src.force();
          if (src.chunk !== null) return [src.chunk, step(src._rest)];
          src = null;
        } else {
          const a = src.a;
          const pos = src.pos;
          if (pos < a.length) {
            const end = Math.min(pos + CHUNK_SIZE, a.length);
            // slice (copy) so a cached chunk never aliases the caller's array
            return [a.slice(pos, end), step({ a, pos: end })];
          }
          src = null;
        }
      }
      const nc = nextColl();
      if (nc === CONCAT_DONE) return null;
      src =
        nc instanceof LazyIterable ? nc : Array.isArray(nc) ? { a: nc, pos: 0 } : chunkCells(nc);
    }
  };
  return new LazyIterable(step(null));
}

export const concat = withApply(
  (...colls) => concat1(colls),
  (colls) => concat1(colls), // lazy seqable argument
);

export function mapcat(f, ...colls) {
  if (colls.length === 0) {
    return comp(map(f), cat);
  }
  return concat1(map(f, ...colls));
}

export function identity(x) {
  return x;
}

export function interleave(...colls) {
  const iters = colls.map((coll) => es6_iterator(iterable(coll)));
  return lazy(function* () {
    while (true) {
      const res = [];
      for (const i of iters) {
        const nextVal = i.next();
        if (nextVal.done) {
          return;
        }
        res.push(nextVal.value);
      }
      yield* res;
    }
  });
}

function interpose1(sep) {
  return transducer((rf) => {
    let started = false;
    return (r, x) => {
      if (!started) {
        started = true;
        return rf(r, x);
      }
      const sepr = rf(r, sep);
      return reduced_QMARK_(sepr) ? sepr : rf(sepr, x);
    };
  });
}

export function interpose(sep, coll) {
  if (arguments.length === 1) return interpose1(sep);
  return drop(1, interleave(repeat(sep), coll));
}

export function select_keys(o, ks) {
  const type = typeConst(o);
  // always a map, like CLJS; a js/Map source keeps its rep
  const ret = type === MAP_TYPE ? new Map() : {};
  // iterable puns nil to no keys and a map to its entries, like CLJS seq
  for (const k of iterable(ks)) {
    const v = get(o, k);
    if (v !== undefined) {
      assoc_BANG_(ret, k, v);
    }
  }
  return ret;
}

export function unreduced(x) {
  if (reduced_QMARK_(x)) {
    return deref(x);
  } else {
    return x;
  }
}

function partition_all1(n) {
  return (rf) => {
    let a = [];
    return (...args) => {
      let result, v;
      switch (args.length) {
        case 0:
          return rf();
        case 1: {
          result = args[0];
          if (a.length !== 0) {
            v = [...a];
            a = [];
            result = unreduced(rf(result, v));
          }
          return rf(result);
        }
        case 2: {
          result = args[0];
          a.push(args[1]);
          if (n === a.length) {
            v = [...a];
            a = [];
            return rf(result, v);
          } else {
            return result;
          }
        }
      }
    };
  };
}

export function partition_all(n, ...args) {
  if (arguments.length === 1) {
    return partition_all1(n);
  }
  let step = n,
    coll = args[0];

  if (args.length === 2) {
    [step, coll] = args;
  }
  return partitionInternal(n, step, [], coll, true);
}

export function partition(n, ...args) {
  // pad stays undefined for arity 1 and 2: only arity 4 provides a pad, which
  // makes the final partial partition be emitted (padded when pad has elements).
  let step = n,
    pad,
    coll = args[0];

  if (args.length === 2) {
    [step, coll] = args;
  } else if (args.length > 2) {
    [step, pad, coll] = args;
  }
  return partitionInternal(n, step, pad, coll, false);
}

export const partitionv = partition; // partition already returns a lazy of arrays
export const partitionv_all = partition_all;

function partitionInternal(n, step, pad, coll, all) {
  return lazyIter(coll, function* (it) {
    let p = [];
    let i = 0;
    for (const x of it) {
      if (i < n) {
        p.push(x);
        if (p.length === n) {
          yield p;
          p = step < n ? p.slice(step) : [];
        }
      }
      i++;
      if (i === step) {
        i = 0;
      }
    }

    if (p.length > 0) {
      if (p.length === n || all) {
        yield p;
      } else if (pad !== undefined) {
        if (pad != null) {
          p.push(...[...iterable(pad)].slice(0, n - p.length));
        }
        yield p;
      }
    }
  });
}

function partition_by1(f) {
  return (rf) => {
    let a = [];
    const none = {};
    let pa = none;
    return (...args) => {
      const l = args.length;
      if (l === 0) {
        return rf();
      }
      if (l === 1) {
        let result = args[0];
        if (a.length !== 0) {
          const v = [...a];
          a = [];
          result = unreduced(rf(result, v));
        }
        return rf(result);
      }
      if (l === 2) {
        const result = args[0];
        const input = args[1];
        const pval = pa;
        const val = f(input);
        pa = val;
        if (pval === none || val === pval) {
          a.push(input);
          return result;
        } else {
          const v = [...a];
          a = [];
          const ret = rf(result, v);
          if (!reduced_QMARK_(ret)) {
            a.push(input);
          }
          return ret;
        }
      }
    };
  };
}

export function partition_by(f, coll) {
  f = __toFn(f);
  if (arguments.length === 1) {
    return partition_by1(f);
  }
  const iter = es6_iterator(coll);
  return lazy(function* () {
    const _fst = iter.next();
    if (_fst.done) {
      yield* null;
    }
    const fst = _fst.value;
    let fv = f(fst);
    let run = [fst];
    let rst = [];
    while (true) {
      const next = iter.next();
      if (next.done) {
        yield run;
        break;
      }
      const _v = next.value;
      const _fv = f(_v);
      if (fv == _fv) {
        run.push(_v);
      } else {
        yield run;
        rst.push(_v);
        run = rst;
        fv = _fv;
        rst = [];
      }
    }
  });
}

export function empty(coll) {
  const type = typeConst(coll);
  if (type != null) {
    if (type === INSTANCE_TYPE) {
      if (coll[IEmptyableCollection__empty] !== undefined) return coll[IEmptyableCollection__empty](coll);
      // a null-prototype object is a map rep; any other instance is not an
      // emptyable collection, like CLJS
      return coll.constructor === undefined ? copyMeta(coll, {}) : null;
    }
    return copyMeta(coll, emptyOfType(type));
  }
  // non-collections give nil, like CLJS
  return null;
}

export function merge(...args) {
  // with no truthy maps, return nil like CLJS `(when (some identity maps) ...)`.
  if (!args.some(truth_)) return null;
  // if the first arg is nil we coerce it into a map.
  const firstArg = args[0];
  let obj;
  if (firstArg === null || firstArg === undefined) {
    obj = {};
  } else if (typeConst(firstArg) === undefined) {
    // a non-collection passes through; conj! throws when maps follow, like CLJS
    obj = firstArg;
  } else if (firstArg[ICollection__conj] !== undefined) {
    // a -conj type is immutable: no defensive copy needed, and a record has
    // no empty to rebuild from
    obj = firstArg;
  } else {
    obj = into(empty(firstArg), firstArg);
  }
  // an ICollection target merges through -conj instead of mutation
  if (obj != null && obj[ICollection__conj] !== undefined) {
    for (let i = 1; i < args.length; i++) {
      obj = obj != null && obj[ICollection__conj] !== undefined
        ? obj[ICollection__conj](obj, args[i])
        : conj_BANG_(obj, args[i]);
    }
    return obj;
  }
  return conj_BANG_(obj, ...args.slice(1));
}

export function key(entry) {
  return entry[0];
}

export function val(entry) {
  return entry[1];
}

export function merge_with(f, ...maps) {
  f = __toFn(f);
  var hasMap = false;
  for (const m of maps) {
    if (m != null) {
      hasMap = true;
      break;
    }
  }
  if (hasMap) {
    const mergeEntry = (m, e) => {
      const k = key(e);
      const v = val(e);
      if (contains_QMARK_(m, k)) {
        return assoc(m, k, f(get(m, k), v));
      } else {
        return assoc(m, k, v);
      }
    };
    const merge2 = (m1, m2) => {
      return reduce(mergeEntry, m1 || {}, seq(m2));
    };
    return reduce(merge2, maps);
  } else {
    return null;
  }
}

export function system_time() {
  return performance.now();
}

export function into(...args) {
  let to, xform, from, c, rf;
  switch (args.length) {
    case 0:
      return [];
    case 1:
      return args[0];
    case 2:
      // vector target bulk-appends chunks (copy preserves metadata); lists conj
      // at the head, other targets need conj!
      to = args[0] ?? [];
      if (isVectorArray(to)) {
        return pushAll(copy(to), args[1]);
      }
      // a type with -conj is immutable: fold through the slot instead of
      // mutating a copy. Direct slot calls keep conj out of the bundle.
      if (to[ICollection__conj] !== undefined) {
        return reduce(
          (acc, x) =>
            acc != null && acc[ICollection__conj] !== undefined
              ? acc[ICollection__conj](acc, x)
              : conj_BANG_(acc, x),
          to,
          args[1]
        );
      }
      return reduce(conj_BANG_, copy(to), args[1]);
    case 3:
      to = args[0];
      xform = args[1];
      from = args[2];
      c = to != null && to[ICollection__conj] !== undefined ? to : copy(to);
      rf = (coll, v) => {
        if (v === undefined) {
          return coll;
        }
        return coll != null && coll[ICollection__conj] !== undefined
          ? coll[ICollection__conj](coll, v)
          : conj_BANG_(coll, v);
      };
      return transduce(xform, rf, c, from);
    default:
      throw TypeError(`Invalid arity call of into: ${args.length}`);
  }
}

export function identical_QMARK_(x, y) {
  return x === y;
}

export function repeat(...args) {
  if (args.length == 0 || args.length > 2) {
    throw new Error(`Invalid arity: ${args.length}`);
  }
  if (args.length == 1) {
    const x = args[0];
    return lazy(function* () {
      while (true) yield x;
    });
  }
  const [n, x] = args;
  return lazy(function* () {
    for (var i = 0; i < n; i++) yield x;
  });
}

export function ensure_reduced(x) {
  if (reduced_QMARK_(x)) {
    return x;
  } else {
    return reduced(x);
  }
}

function take1(n) {
  return transducer((rf) => {
    let na = n;
    return (r, x) => {
      const nn = --na + 1;
      if (nn > 0) r = rf(r, x);
      return nn > 1 ? r : ensure_reduced(r);
    };
  });
}

function assertNumber(n) {
  if (typeof n !== 'number') {
    throw new Error('Assert failed: (number? n)');
  }
  return n;
}

export function take(n, coll) {
  assertNumber(n);
  if (arguments.length === 1) {
    return take1(n);
  }
  return lazyIter(coll, function* (it) {
    let i = n - 1;
    for (const x of it) {
      if (i-- >= 0) {
        yield x;
      }
      if (i < 0) {
        return;
      }
    }
  });
}

export function take_last(n, coll) {
  assertNumber(n);
  if (n <= 0) {
    return null;
  }
  if (Array.isArray(coll)) {
    return seq(coll.slice(-n));
  } else {
    const lastN = new Array(n);
    let i = 0;
    for (const x of iterable(coll)) {
      lastN[i % n] = x;
      i++;
    }
    // an empty or nil coll has no last n elements; return nil like CLJS.
    if (i === 0) return null;
    if (i % n !== 0 && i >= n) {
      return lastN.slice(i % n).concat(lastN.slice(0, i % n));
    } else {
      lastN.length = Math.min(i, n);
      return lastN;
    }
  }
}

function take_while1(pred) {
  return transducer((rf) => (r, x) => (truth_(pred(x)) ? rf(r, x) : reduced(r)));
}

export function take_while(pred, coll) {
  pred = __toFn(pred);
  if (arguments.length === 1) {
    return take_while1(pred);
  }
  return lazyIter(coll, function* (it) {
    for (const o of it) {
      if (truth_(pred(o))) yield o;
      else return;
    }
  });
}

function take_nth1(n) {
  return transducer((rf) => {
    let ia = -1;
    return (r, x) => (rem(++ia, n) === 0 ? rf(r, x) : r);
  });
}

export function take_nth(n, coll) {
  assertNumber(n);
  if (arguments.length === 1) return take_nth1(n);
  if (n <= 0) {
    return repeat(first(coll));
  }

  return lazyIter(coll, function* (it) {
    let i = 0;
    for (const x of it) {
      if (i % n === 0) {
        yield x;
      }
      i++;
    }
  });
}

export function partial(f, ...xs) {
  f = __toFn(f);
  return function (...args) {
    return f(...xs, ...args);
  };
}

export function cycle(coll) {
  // seq the coll eagerly: nil or empty cycles to an empty seq and a
  // non-iterable throws, like the seq call in CLJS cycle. The first lap
  // is cached and replayed, like the retained seq in CLJS Cycle, so a
  // one-shot iterator source cycles instead of ending after one lap.
  const it = iterable(coll);
  return lazy(function* () {
    const cache = [];
    for (const x of it) {
      cache.push(x);
      yield x;
    }
    while (cache.length) yield* cache;
  });
}

function drop1(n) {
  return transducer((rf) => {
    let na = n;
    return (r, x) => (na-- > 0 ? r : rf(r, x));
  });
}

export function drop(n, xs) {
  assertNumber(n);
  if (arguments.length === 1) return drop1(n);
  return lazyIter(xs, function* (iter) {
    for (let x = 0; x < n; x++) {
      iter.next();
    }
    yield* iter;
  });
}

function drop_while1(pred) {
  return transducer((rf) => {
    let da = true;
    return (r, x) => {
      if (da && truth_(pred(x))) return r;
      da = false;
      return rf(r, x);
    };
  });
}

export function drop_while(pred, xs) {
  pred = __toFn(pred);
  if (arguments.length === 1) return drop_while1(pred);
  return lazyIter(xs, function* (iter) {
    while (true) {
      const nextItem = iter.next();
      if (nextItem.done) {
        break;
      }
      const value = nextItem.value;
      if (!truth_(pred(value))) {
        yield value;
        break;
      }
    }
    yield* iter;
  });
}

function distinct1() {
  return transducer((rf) => {
    const seen = new Set();
    return (r, x) => {
      if (seen.has(x)) return r;
      seen.add(x);
      return rf(r, x);
    };
  });
}

export function distinct(coll) {
  if (arguments.length === 0) return distinct1();
  return lazyIter(coll, function* (it) {
    const seen = new Set();
    for (const x of it) {
      if (!seen.has(x)) yield x;
      seen.add(x);
    }
    return;
  });
}

const DEDUPE_NONE = Symbol('dedupe-none');

function dedupe1() {
  return transducer((rf) => {
    let prev = DEDUPE_NONE;
    return (r, x) => {
      const skip = prev !== DEDUPE_NONE && truth_(_EQ_(prev, x));
      prev = x;
      return skip ? r : rf(r, x);
    };
  });
}

export function dedupe(coll) {
  if (arguments.length === 0) return dedupe1();
  return lazyIter(coll, function* (it) {
    let prev = DEDUPE_NONE;
    for (const x of it) {
      if (prev === DEDUPE_NONE || !truth_(_EQ_(prev, x))) yield x;
      prev = x;
    }
    return;
  });
}

export function update(coll, k, f, ...args) {
  f = __toFn(f);
  return assoc(coll, k, f(get(coll, k), ...args));
}

export function get_in(coll, path, orElse) {
  let entry = coll;
  // iterable puns a nil path to an empty path, like CLJS
  for (const item of iterable(path)) {
    entry = get(entry, item);
  }
  if (entry === undefined) return orElse;
  return entry;
}

export function update_in(coll, path, f, ...args) {
  f = __toFn(f);
  return assoc_in(coll, path, f(get_in(coll, path), ...args));
}

export function fnil(f, ...defaults) {
  f = __toFn(f);
  const n = defaults.length;
  return function (...args) {
    for (let i = 0; i < n; i++) {
      if (args[i] == null) args[i] = defaults[i];
    }
    return f(...args);
  };
}

export function every_QMARK_(pred, coll) {
  pred = __toFn(pred);
  if (Array.isArray(coll)) {
    for (let i = 0; i < coll.length; i++) if (!pred(coll[i])) return false;
    return true;
  }
  const next = chunkCursor(coll);
  let ch;
  while ((ch = next()) !== null) {
    for (let i = 0; i < ch.length; i++) if (!pred(ch[i])) return false;
  }
  return true;
}

export function not_every_QMARK_(pred, coll) {
  return !every_QMARK_(pred, coll);
}

function keep1(pred) {
  return transducer((rf) => (r, x) => {
    const v = pred(x);
    return v == null ? r : rf(r, v);
  });
}

export function keep(pred, coll) {
  pred = __toFn(pred);
  if (arguments.length === 1) return keep1(pred);
  return mapChunks(coll, (ch) => {
    const out = [];
    for (let i = 0; i < ch.length; i++) {
      const res = pred(ch[i]);
      if (truth_(res)) out.push(res);
    }
    return out;
  });
}

export function reverse(coll) {
  return toArray(coll).reverse();
}

export function reversible_QMARK_(x) {
  return isVectorArray(x) || (x != null && x[SORTED_TAG] === true);
}

export function rseq(x) {
  // vectors and sorted maps/sets are reversible, like CLJS
  if (isVectorArray(x)) {
    return x.length === 0 ? null : [...x].reverse();
  }
  if (x != null && x[SORTED_TAG] === true) {
    const xs = [...x].reverse();
    return xs.length === 0 ? null : xs;
  }
  throw new Error('rseq not supported on: ' + typeof x);
}

export function sort(f, coll) {
  if (arguments.length === 1) {
    coll = f;
    f = undefined;
  }
  f = __toFn(f);
  // need a copy since .sort works in place and .toSorted isn't on Node < 20
  const clone = toArray(coll);
  // result is guaranteed to be stable since ES2019, like CLJS
  return clone.sort(f || compare);
}

function fnToComparator(f) {
  if (f === compare) {
    return f;
  }
  return (x, y) => {
    const r = f(x, y);
    if (number_QMARK_(r)) {
      return r;
    }
    if (r) {
      return -1;
    }
    if (f(y, x)) {
      return 1;
    }
    return 0;
  };
}

export function sort_by(keyfn, comp, coll) {
  if (arguments.length === 2) {
    coll = comp;
    comp = compare;
  }
  keyfn = __toFn(keyfn);
  comp = __toFn(comp);
  return sort((x, y) => {
    const f = fnToComparator(comp);
    const kx = keyfn(x);
    const ky = keyfn(y);
    return f(kx, ky);
  }, coll);
}

export function shuffle(coll) {
  const result = toArray(coll);
  let remaining = result.length;
  while (remaining) {
    const i = Math.floor(Math.random() * remaining--);
    const tmp = result[remaining];
    result[remaining] = result[i];
    result[i] = tmp;
  }

  return result;
}

export function some(pred, coll) {
  pred = __toFn(pred);
  if (Array.isArray(coll)) {
    for (let i = 0; i < coll.length; i++) {
      const res = pred(coll[i]);
      if (truth_(res)) return res;
    }
    return undefined;
  }
  const next = chunkCursor(coll);
  let ch;
  while ((ch = next()) !== null) {
    for (let i = 0; i < ch.length; i++) {
      const res = pred(ch[i]);
      if (truth_(res)) return res;
    }
  }
  return undefined;
}

export function not_any_QMARK_(pred, coll) {
  pred = __toFn(pred);
  return !some(pred, coll);
}

export function replace(smap, coll) {
  const mapf = Array.isArray(coll) ? mapv : map;
  return mapf((x) => {
    const repl = smap[x];
    if (repl !== undefined) {
      return repl;
    } else {
      return x;
    }
  }, coll);
}

export function empty_QMARK_(coll) {
  return seq(coll) ? false : true;
}

export function rand(n) {
  if (undefined === n) {
    n = 1;
  }
  return Math.random() * n;
}

export function rand_int(n) {
  return Math.floor(Math.random() * n);
}

export function rand_nth(coll) {
  const ri = rand_int(count(coll));
  return nth(coll, ri);
}

function _repeatedly(f) {
  return lazy(function* () {
    while (true) yield f();
  });
}

export function repeatedly(n, f) {
  if (arguments.length === 1) {
    f = n;
    n = undefined;
  }
  const res = _repeatedly(f);
  if (n !== undefined) {
    if (typeof n !== 'number') {
      throw new Error('repeatedly: count must be a number, got: ' + str(n));
    }
    return take(n, res);
  } else {
    return res;
  }
}

export function update_BANG_(m, k, f, ...args) {
  f = __toFn(f);
  const v = get(m, k);
  return assoc_BANG_(m, k, f(v, ...args));
}

export function group_by(f, coll) {
  f = __toFn(f);
  const res = {};
  for (const o of iterable(coll)) {
    const key = f(o);
    update_BANG_(res, key, fnil(conj_BANG_, []), o);
  }
  return res;
}

export function frequencies(coll) {
  const res = {};
  const uf = fnil(inc, 0);
  for (const o of iterable(coll)) {
    update_BANG_(res, o, uf);
  }
  return res;
}

// The lazy-seq macro emits `new LazySeq(() => body)`: a LazyIterable whose step
// evaluates the body thunk on first force and reads it unchunked.
export class LazySeq extends LazyIterable {
  // Core fns build the base LazyIterable, not this subclass, so widen
  // instanceof to any lazy cell for `(instance? LazySeq x)` parity with CLJS.
  static [Symbol.hasInstance](x) {
    return x instanceof LazyIterable;
  }
  constructor(f) {
    super(() => unchunkedSteps(es6_iterator(iterable(f())))());
  }
}

export function butlast(coll) {
  const x = toArray(coll);
  x.pop();
  return x.length > 0 ? x : null;
}

export function drop_last(...args) {
  const [n, coll] = args.length > 1 ? args : [1, args[0]];
  return map((x, _) => x, coll, drop(n, coll));
}

export function split_at(n, coll) {
  return [take(n, coll), drop(n, coll)];
}

export function split_with(pred, coll) {
  return [take_while(pred, coll), drop_while(pred, coll)];
}

export function count(coll) {
  if (!coll) return 0;
  const len = coll.length || coll.size;
  if (typeof len === 'number') {
    return len;
  }
  if (coll[ICounted__count] !== undefined) return coll[ICounted__count](coll);
  // sum chunk lengths instead of counting elements one by one
  const next = chunkCursor(coll);
  let ret = 0;
  let ch;
  while ((ch = next()) !== null) ret += ch.length;
  return ret;
}

export function true_QMARK_(x) {
  return x === true;
}

export function false_QMARK_(x) {
  return x === false;
}

export function some_QMARK_(x) {
  return !(x === null || x === undefined);
}

export function boolean$(x) {
  return truth_(x);
}

export function parse_boolean(s) {
  if (typeof s !== 'string') {
    throw new Error('Argument must be a string');
  }
  if (s === 'true') return true;
  if (s === 'false') return false;
  return null;
}

export function zero_QMARK_(x) {
  return x === 0;
}

export function neg_QMARK_(x) {
  return x < 0;
}

export function pos_QMARK_(x) {
  return x > 0;
}

export function js_obj(...args) {
  let ctr = 0;
  const ret = {};
  for (;;) {
    if (ctr >= args.length) {
      break;
    }
    ret[args[ctr]] = args[ctr + 1];
    ctr = ctr + 2;
  }
  return ret;
}

export function alength(arr) {
  return arr.length;
}

export function aset(arr, idx, val, ...more) {
  if (more.length == 0) {
    arr[idx] = val;
    return val;
  } else {
    const path = [idx, val, ...more];
    const _val = path[path.length - 1];
    let innerArray = arr;
    let _idx = 0;
    const _pathLen = path.length - 2;
    for (; _idx < _pathLen; _idx++) {
      innerArray = innerArray[path[_idx]];
    }
    innerArray[path[_idx]] = _val;
    return val;
  }
}

export function dorun(x) {
  // only a lazy seq needs forcing; realized colls are walked natively
  if (x instanceof LazyIterable) {
    const next = chunkCursor(x);
    while (next() !== null);
    return null;
  }
  for (const _ of iterable(x)) {
    // nothing here, just consume for side effects
  }
  return null;
}

export function doall(x) {
  // realize a lazy seq and return it, like CLJS; anything else is
  // already concrete
  if (x != null && x[TYPE_TAG] === LAZY_ITERABLE_TYPE) for (const _ of x);
  return x;
}

export function aclone(arr) {
  const cloned = [...arr];
  return cloned;
}

function typed_array(sizeOrSeq, initValOrSeq) {
  if (initValOrSeq !== undefined) {
    const a = new Array(sizeOrSeq);
    if (typeof initValOrSeq === 'number') return a.fill(initValOrSeq);
    let i = 0;
    for (const x of iterable(initValOrSeq)) {
      if (i >= sizeOrSeq) break;
      a[i++] = x;
    }
    return a;
  }
  if (typeof sizeOrSeq === 'number') return new Array(sizeOrSeq).fill(null);
  return [...iterable(sizeOrSeq)];
}

// like CLJS: provided for compatibility, all make a plain array
export function int_array(sizeOrSeq, initValOrSeq) {
  return typed_array(sizeOrSeq, initValOrSeq);
}
export function long_array(sizeOrSeq, initValOrSeq) {
  return typed_array(sizeOrSeq, initValOrSeq);
}
export function float_array(sizeOrSeq, initValOrSeq) {
  return typed_array(sizeOrSeq, initValOrSeq);
}
export function double_array(sizeOrSeq, initValOrSeq) {
  return typed_array(sizeOrSeq, initValOrSeq);
}
export function object_array(sizeOrSeq, initValOrSeq) {
  return typed_array(sizeOrSeq, initValOrSeq);
}

export function add_watch(ref, key, fn) {
  if (ref?.[IWatchable__add_watch] !== undefined) {
    ref[IWatchable__add_watch](ref, key, fn);
    return ref;
  }
  nilImpl(_add_watch, 'IWatchable.-add-watch', ref)(ref, key, fn);
  return ref;
}

export function remove_watch(ref, key) {
  if (ref?.[IWatchable__remove_watch] !== undefined) {
    ref[IWatchable__remove_watch](ref, key);
    return ref;
  }
  nilImpl(_remove_watch, 'IWatchable.-remove-watch', ref)(ref, key);
  return ref;
}

export function reduce_kv(f, init, m) {
  if (!m) {
    return init;
  }
  if (m[IKVReduce__kv_reduce] !== undefined) return m[IKVReduce__kv_reduce](m, f, init);
  var ret = init;
  for (const o of iterable(m)) {
    ret = f(ret, o[0], o[1]);
  }
  return ret;
}

// CLJS reduce of (cond (NaN? x) x (NaN? y) y (> x y) x :else y): returns one of
// the values, so nil acts like zero, and a NaN propagates. NaN? is js/isNaN.
export function max(x, ...more) {
  let m = x;
  for (const y of more) m = isNaN(m) ? m : isNaN(y) ? y : m > y ? m : y;
  return m;
}

export function min(x, ...more) {
  let m = x;
  for (const y of more) m = isNaN(m) ? m : isNaN(y) ? y : m < y ? m : y;
  return m;
}

export function associative_QMARK_(x) {
  switch (typeConst(x)) {
    case MAP_TYPE:
    case ARRAY_TYPE:
    case OBJECT_TYPE:
      return true;
    case INSTANCE_TYPE:
      return x[IAssociative__assoc] !== undefined;
    default:
      return false;
  }
}

export function map_QMARK_(coll) {
  if (coll == null) return false;
  if (isObj(coll)) return true;
  if (coll instanceof Map) return true;
  if (coll[TYPE_TAG] === MAP_TYPE) return true;
  if (coll[IMap.__sym] !== undefined) return true;
  return false;
}

export function every_pred(...preds) {
  return (...args) => {
    for (const p of preds) {
      for (const a of args) {
        const res = p(a);
        if (!res) {
          return false;
        }
      }
    }
    return true;
  };
}

export function some_fn(...fns) {
  return (...args) => {
    let res;
    for (const f of fns) {
      for (const a of args) {
        res = f(a);
        // truth_, not JS truthiness: 0 and "" are truthy in CLJS
        if (truth_(res)) return res;
      }
    }
    return res;
  };
}

export function into_array(type, aseq) {
  const theSeq = aseq || type;
  return vec(theSeq);
}

export function iterate(f, x) {
  var current = x;
  return lazy(function* () {
    while (true) {
      yield current;
      current = f(current);
    }
  });
}

export function juxt(...fs) {
  fs = fs.map(__toFn);
  return (...args) => {
    const ret = [];
    for (const f of fs) {
      ret.push(f(...args));
    }
    return ret;
  };
}

export function next(x) {
  if (Array.isArray(x)) {
    const ret = x.slice(1);
    if (ret.length > 0) {
      return ret;
    } else {
      return null;
    }
  } else {
    return seq(rest(x));
  }
}

export function nnext(x) {
  return next(next(x));
}

export function nthnext(coll, n) {
  let xs = seq(coll);
  while (xs != null && n > 0) {
    xs = next(xs);
    n = n - 1;
  }
  return xs;
}

export function nthrest(coll, n) {
  let xs = coll;
  while (n > 0 && seq(xs) != null) {
    xs = rest(xs);
    n = n - 1;
  }
  return xs;
}

export function compare(x, y) {
  if (x === y) {
    return 0;
  } else {
    if (x == null) {
      return -1;
    }
    if (y == null) {
      return 1;
    }
    // keywords sort by name, also against plain strings
    if (isKw(x) || isKw(y)) {
      return compare(String(x), String(y));
    }
    const tx = typeof x;
    const ty = typeof y;
    if ((tx === 'number' && ty === 'number') || (tx === 'string' && ty === 'string') ||
        (tx === 'boolean' && ty === 'boolean')) {
      if (x === y) {
        return 0;
      }
      if (x < y) {
        return -1;
      }
      return 1;
    } else if (Array.isArray(x) && Array.isArray(y)) {
      // Implemented like `APersistentVector.compareTo`.
      if (x.length < y.length) {
        return -1;
      } else if (x.length > y.length) {
        return 1;
      } else {
        for (let i = 0; i < x.length; i++) {
          const c = compare(x[i], y[i]);
          if (c != 0) {
            return c;
          }
        }
        return 0;
      }
    } else {
      throw new Error(`comparing ${tx} to ${ty}`);
    }
  }
}

export function to_array(aseq) {
  return into_array(aseq);
}

export function truth_(x) {
  return x != null && x !== false;
}

export const t = truth_; // backward compat, remove in 2025

export function subs(s, start, end) {
  return s.substring(start, end);
}

export function fn_QMARK_(x) {
  return 'function' === typeof x;
}

export function ifn_QMARK_(x) {
  return fn_QMARK_(x);
}

export function any_QMARK_(_x) {
  return true;
}

export function distinct_QMARK_(x, ...more) {
  if (more.length === 0) return true;
  const seen = [x];
  for (const y of more) {
    for (const s of seen) {
      if (truth_(_EQ_(s, y))) return false;
    }
    seen.push(y);
  }
  return true;
}

export function re_seq(re, s) {
  return lazy(function* () {
    while (true) {
      const matches = re.exec(s);
      if (matches) {
        const match_str = matches[0];
        const match_vals = matches.length === 1 ? match_str : vec(matches);
        yield match_vals;
        const post_idx = matches.index + max(1, match_str.length);
        if (post_idx > s.length) break;
        s = subs(s, post_idx);
      } else break;
    }
  });
}

export function NaN_QMARK_(x) {
  // coercing, like CLJS js/isNaN: (NaN? "foo") is true
  return isNaN(x);
}

export function number_QMARK_(x) {
  return typeof x == 'number';
}

export function keys(obj) {
  if (obj == null) return null;
  const t = typeConst(obj);
  switch (t) {
    case INSTANCE_TYPE:
      // Object.keys on an instance would leak internals: go through -kv-reduce
      if (obj[IKVReduce__kv_reduce] !== undefined) {
        const ks = obj[IKVReduce__kv_reduce](obj, (acc, k, _) => (acc.push(k), acc), []);
        if (ks.length) return ks;
        return;
      }
    // fall through
    case OBJECT_TYPE: {
      const ks = Object.keys(obj);
      if (ks.length) return ks;
      return;
    }
    case MAP_TYPE:
      if (obj.size) return Array.from(obj.keys());
      return;
  }
  // not a map: nil when empty, like seq in CLJS, else throw
  for (const _ of iterable(obj)) {
    throw new TypeError(obj + ' is not a map');
  }
  return null;
}

export function js_keys(obj) {
  return keys(obj) ?? [];
}

export function vals(obj) {
  if (obj == null) return null;
  const t = typeConst(obj);
  switch (t) {
    case INSTANCE_TYPE:
      if (obj[IKVReduce__kv_reduce] !== undefined) {
        const vs = obj[IKVReduce__kv_reduce](obj, (acc, _, v) => (acc.push(v), acc), []);
        if (vs.length) return vs;
        return;
      }
    // fall through
    case OBJECT_TYPE: {
      const vs = Object.values(obj);
      if (vs.length) return vs;
      return;
    }
    case MAP_TYPE:
      if (obj.size) return Array.from(obj.values());
      return;
  }
  // not a map: nil when empty, like seq in CLJS, else throw
  for (const _ of iterable(obj)) {
    throw new TypeError(obj + ' is not a map');
  }
  return null;
}

export function string_QMARK_(s) {
  // a keyword is a String subclass: string? stays true for it, like the
  // string representation. keyword? identifies the subtype.
  return typeof s === 'string' || isKw(s);
}

export function unchecked_int(x) {
  return Math.trunc(x);
}

export function unchecked_inc_int(x) {
  return x + 1;
}

export function unchecked_dec_int(x) {
  return x - 1;
}

export function unchecked_add_int(x, y) {
  return x + y;
}

// keywords/symbols are strings in squint; namespace is the part before "/"
export function namespace(x) {
  // keywords/symbols are strings in squint; namespace is the part before the
  // "/" ns separator (consistent with `name`, which returns the part after).
  // i >= 1 so a leading "/" yields a nil namespace rather than an empty one.
  const i = x.indexOf('/');
  return i >= 1 ? x.slice(0, i) : null;
}

// (keyword name) or (keyword ns name): the interned keyword object
export function keyword(arg1, arg2) {
  return kw(arg1, arg2);
}

// Opt-in keyword objects ({:squint/keywords true} ns metadata): interned,
// extends String so string ops, property access, str and JSON behave like
// the default representation, but the type survives to a wire boundary
// (e.g. a transit or edn write handler emitting a real keyword). The pure
// IIFE keeps the class out of bundles that never construct one.
const Keyword = /* @__PURE__ */ (() => {
  class Keyword extends String {
  }
  // a keyword equals another keyword or its own name string, so opted-in
  // data mixes with the default representation
  Keyword.prototype[IEquiv__equiv] = (self, other) =>
    isKw(other) ? String(self) === String(other) : typeof other === 'string' && String(self) === other;
  // hash like the name string, consistent with equiv
  Keyword.prototype[IHash__hash] = (self) => m3HashInt(hashString(String(self)));
  Keyword.prototype[IPrintWithWriter__pr_writer] = (self, writer, _opts) =>
    writer[IWriter__write](writer, ':' + self);
  // not char-seqable, unlike a string; String's inherited iterator would
  // make seq/walk recurse into the characters
  Keyword.prototype[Symbol.iterator] = undefined;
  Keyword.prototype[TYPE_TAG] = KEYWORD_TYPE;
  return Keyword;
})();

// interning holds keywords weakly, like Clojure's keyword table: identity is
// only needed among live instances, so a dead entry may be re-interned as a
// fresh instance later
const keywordCache = /* @__PURE__ */ new Map();

const keywordRegistry = /* @__PURE__ */ new FinalizationRegistry((fqn) => {
  const ref = keywordCache.get(fqn);
  // the same fqn may have been re-interned by the time this fires
  if (ref !== undefined && ref.deref() === undefined) keywordCache.delete(fqn);
});

// the other representation of a keyword/string key, so Set/js Map
// membership follows = (a keyword equals its name string)
function altKey(k) {
  if (typeof k === 'string') return keywordCache.get(k)?.deref();
  if (isKw(k)) return String(k);
  return undefined;
}

// (kw name), (kw ns name) or (kw k): the interned keyword object for a name
export function kw(arg1, arg2) {
  if (isKw(arg1)) return arg1;
  const fqn = arg2 !== undefined ? (arg1 != null ? arg1 + '/' : '') + arg2 : arg1;
  if (fqn == null) return null;
  let k = keywordCache.get(fqn)?.deref();
  if (k === undefined) {
    k = new Keyword(fqn);
    keywordCache.set(fqn, new WeakRef(k));
    keywordRegistry.register(k, fqn);
  }
  return k;
}

export function symbol(arg1, arg2) {
  if (arg2 !== undefined) {
    return (arg1 != null ? arg1 + '/' : '') + arg2;
  }
  return arg1;
}

export function keyword_QMARK_(x) {
  return isKw(x);
}

// squint has no symbol type either; symbols are strings
export function symbol_QMARK_(x) {
  return typeof x === 'string';
}

// squint has no first-class Var objects (#'x emits the value), so var? is false
export function var_QMARK_(_x) {
  return false;
}

export function simple_keyword_QMARK_(x) {
  return isKw(x) && !x.includes('/');
}

export function qualified_keyword_QMARK_(x) {
  return isKw(x) && x.includes('/');
}

export function ident_QMARK_(x) {
  return typeof x === 'string' || isKw(x);
}

export function simple_ident_QMARK_(x) {
  return ident_QMARK_(x) && namespace(x) == null;
}

export function qualified_ident_QMARK_(x) {
  return ident_QMARK_(x) && namespace(x) != null;
}

export function simple_symbol_QMARK_(x) {
  return symbol_QMARK_(x) && namespace(x) == null;
}

export function qualified_symbol_QMARK_(x) {
  return symbol_QMARK_(x) && namespace(x) != null;
}

export function coll_QMARK_(coll) {
  return typeConst(coll) != undefined;
}

export function regexp_QMARK_(coll) {
  return coll instanceof RegExp;
}

class ExceptionInfo extends Error {
  constructor(message, data, cause) {
    super(message);
    this._data = data;
    this._cause = cause;
  }
}

export function ex_data(e) {
  if (e instanceof ExceptionInfo) return e._data;
  else return null;
}

export function ex_message(e) {
  if (e instanceof Error) return e.message;
  else return null;
}

export function ex_cause(e) {
  if (e instanceof ExceptionInfo) return e._cause;
  else return null;
}

export function ex_info(message, data, cause) {
  return new ExceptionInfo(message, data, cause);
}

export function int_QMARK_(x) {
  return Number.isInteger(x);
}

export function double_QMARK_(x) {
  return typeof x === 'number';
}

// like CLJS: every number is a float
export function float_QMARK_(x) {
  return double_QMARK_(x);
}

export const integer_QMARK_ = int_QMARK_;

export function pos_int_QMARK_(x) {
  return int_QMARK_(x) && x > 0;
}

export function nat_int_QMARK_(x) {
  return int_QMARK_(x) && x > -1;
}

export function neg_int_QMARK_(x) {
  return int_QMARK_(x) && x < 0;
}

export function meta(x) {
  if (x != null && x[IMeta__meta] !== undefined) {
    return x[IMeta__meta](x);
  }
  return null;
}

export function with_meta(x, m) {
  if (x != null && x[IWithMeta__with_meta] !== undefined) {
    return x[IWithMeta__with_meta](x, m);
  }
  return with_meta_copy(x, m);
}

// instance-level impls for a plain value; the meta lives in the closure
function installMeta(x, m) {
  x[IMeta__meta] = () => m;
  x[IWithMeta__with_meta] = with_meta_copy;
  return x;
}

function with_meta_copy(x, m) {
  // For functions, wrap in a new callable that forwards to the original
  // so fn? stays true and the original isn't mutated. copy() can't handle
  // functions - a {...x} spread loses the call signature.
  if (typeof x === 'function') {
    const wrapped = function (...args) { return x.apply(this, args); };
    return installMeta(wrapped, m);
  }
  // A lazy seq or cons is not copied element-wise: clone the head so the new
  // value carries its own metadata without forcing realization.
  if (x?.[TYPE_TAG] === LAZY_ITERABLE_TYPE) {
    const ret = Object.assign(Object.create(Object.getPrototypeOf(x)), x);
    return installMeta(ret, m);
  }
  return installMeta(copy(x), m);
}

export function vary_meta(x, f, ...args) {
  return with_meta(x, f(meta(x), ...args));
}

export function boolean_QMARK_(x) {
  return x === true || x === false;
}

export function counted_QMARK_(x) {
  const tc = typeConst(x);
  switch (tc) {
    case ARRAY_TYPE:
    case MAP_TYPE:
    case OBJECT_TYPE:
    case INSTANCE_TYPE:
    case LIST_TYPE:
    case SET_TYPE:
      return true;
  }
  return false;
}

export function bounded_count(n, coll) {
  if (counted_QMARK_(coll)) {
    return count(coll);
  } else {
    return count(take(n, coll));
  }
}

export function find(m, k) {
  const v = get(m, k);
  if (v !== undefined) {
    return tagMapEntry([k, v]);
  }
}

export function mod(x, y) {
  return ((x % y) + y) % y;
}

export function min_key(k, x, ...more) {
  k = __toFn(k);
  // pairwise (if (< (k x) (k y)) x y), like CLJS, so NaN propagates right
  let min = x;
  for (const y of more) {
    if (!(k(min) < k(y))) min = y;
  }
  return min;
}

export function max_key(k, x, ...more) {
  k = __toFn(k);
  // pairwise (if (> (k x) (k y)) x y), like CLJS, so NaN propagates right
  let max = x;
  for (const y of more) {
    if (!(k(max) > k(y))) max = y;
  }
  return max;
}

function parsing_err(x) {
  throw new Error(`Expected string, got: ${typeof x}`);
}

export function parse_long(s) {
  if (string_QMARK_(s)) {
    if (/^[+-]?\d+$/.test(s)) {
      const i = parseInt(s);
      if (Number.MIN_SAFE_INTEGER <= i && i <= Number.MAX_SAFE_INTEGER) {
        return i;
      }
    }
    return null;
  }
  return parsing_err(s);
}

export function parse_double(s) {
  if (string_QMARK_(s)) {
    // eslint-disable-next-line no-control-regex -- \x00-\x20 mirrors Java trim
    if (/^[\x00-\x20]*[+-]?NaN[\x00-\x20]*$/.test(s)) {
      return NaN;
    } else if (
      // eslint-disable-next-line no-control-regex -- \x00-\x20 mirrors Java trim
      /^[\x00-\x20]*[+-]?(Infinity|((\d+\.?\d*|\.\d+)([eE][+-]?\d+)?)[dDfF]?)[\x00-\x20]*$/.test(
        s
      )
    ) {
      return parseFloat(s);
    } else {
      return null;
    }
  } else {
    return parsing_err(s);
  }
}

function fix(q) {
  if (q >= 0) {
    return Math.floor(q);
  }
  return Math.ceil(q);
}

export function quot(n, d) {
  const rem = n % d;
  return fix((n - rem) / d);
}

export function trampoline(f, ...args) {
  if (args.length == 0) {
    // eslint-disable-next-line no-constant-condition
    while (true) {
      const ret = f();
      if (truth_(fn_QMARK_(ret))) {
        f = ret;
        continue;
      } else {
        return ret;
      }
    }
  } else {
    return trampoline(function () {
      return apply(f, args);
    });
  }
}

export function transduce(xform, ...args) {
  switch (args.length) {
    case 2: {
      const f = args[0];
      const coll = args[1];
      return transduce(xform, f, f(), coll);
    }
    default: {
      let f = args[0];
      const init = args[1];
      const coll = args[2];
      f = xform(f);
      const ret = reduce(f, init, coll);
      return f(ret);
    }
  }
}

export function zipmap(keys, vals) {
  const res = {};
  const keyIterator = iterable(keys)[Symbol.iterator]();
  const valIterator = iterable(vals)[Symbol.iterator]();
  let nextKey, nextVal;
  for (;;) {
    nextKey = keyIterator.next();
    if (nextKey.done) break;
    nextVal = valIterator.next();
    if (nextVal.done) break;
    res[nextKey.value] = nextVal.value;
  }
  return res;
}

export function not_empty(x) {
  const isSeq = seq(x);
  if (isSeq) {
    return x;
  } else return null;
}

export function tree_seq(isBranch, children, root) {
  const walk = function* (node) {
    yield node;
    if (truth_(isBranch(node))) {
      for (const c of iterable(children(node))) {
        yield* walk(c);
      }
    }
  };
  return lazy(function* () {
    yield* walk(root);
  });
}

export function flatten(x) {
  return filter(complement(sequential_QMARK_), rest(tree_seq(sequential_QMARK_, seq, x)));
}

export function transient$(x) {
  if (x != null && x[IEditableCollection__as_transient] !== undefined) return x[IEditableCollection__as_transient](x);
  return copy(x);
}

export function persistent_BANG_(x) {
  if (x != null && x[ITransientCollection__persistent_BANG_] !== undefined) return x[ITransientCollection__persistent_BANG_](x);
  // no Object.freeze: persistent structures stay extensible so symbol-keyed
  // metadata can be attached
  return x;
}

const SortedSet = defclass(
class SortedSet {
  constructor(xs, cmp) {
    this[TYPE_TAG] = SET_TYPE;
    this[SORTED_TAG] = true;
    this._cmp = cmp ?? xs?._cmp;
    const isSorted = xs instanceof SortedSet && xs._cmp === this._cmp;
    if (!isSorted) {
      xs = this._cmp ? sort(this._cmp, xs) : sort(xs);
    }
    const s = new Set(xs);
    // we don't re-use xs since xs can contain duplicates
    this._elts = [...s];
    this._set = s;
    this.size = this._elts.length;
  }
  add(x) {
    if (this._set.has(x)) return this;
    const xs = this._elts;
    const cmp = this._cmp;
    let added = false;
    for (let i = 0; i < xs.length; i++) {
      if ((cmp ? cmp(x, xs[i]) : compare(x, xs[i])) <= 0) {
        xs.splice(i, 0, x);
        added = true;
        break;
      }
    }
    if (!added) {
      xs.push(x);
      // in this case we can re-use the set, since we add the element last
      this._set.add(x);
    } else {
      this._set = new Set(xs);
    }
    this.size = xs.length;
    return this;
  }
  delete(x) {
    if (!this._set.has(x)) return this;
    const xs = this._elts;
    const idx = xs.indexOf(x);
    xs.splice(idx, 1);
    this._set = new Set(xs);
    this.size = xs.length;
    return this;
  }
  has(x) {
    return this._set.has(x);
  }
  keys() {
    return this.values();
  }
  values() {
    return this._elts[Symbol.iterator]();
  }
  entries() {
    return this._set.entries();
  }
  forEach(...xs) {
    return this.set.forEach(...xs);
  }
  clear() {
    this._elts = [];
    this._set = new Set(this._elts);
  }
  [Symbol.iterator]() {
    return this.keys();
  }
}
);

export function sorted_set(...xs) {
  return new SortedSet(xs);
}

export function sorted_set_by(cmp, ...xs) {
  return new SortedSet(xs, fnToComparator(__toFn(cmp)));
}

// A map that keeps its keys in sorted order. Backed by a sorted key array plus
// a plain Map for lookup, and branded MAP_TYPE so map ops dispatch normally.
const SortedMap = defclass(
class SortedMap {
  constructor(entries, cmp) {
    this[TYPE_TAG] = MAP_TYPE;
    this[SORTED_TAG] = true;
    this._cmp = cmp ?? entries?._cmp;
    this._map = new Map();
    this._keys = [];
    this.size = 0;
    if (entries) {
      for (const [k, v] of entries) this.set(k, v);
    }
  }
  set(k, v) {
    if (!this._map.has(k)) {
      const ks = this._keys;
      const cmp = this._cmp;
      let i = 0;
      while (i < ks.length && (cmp ? cmp(k, ks[i]) : compare(k, ks[i])) > 0) i++;
      ks.splice(i, 0, k);
    }
    this._map.set(k, v);
    this.size = this._map.size;
    return this;
  }
  get(k) {
    return this._map.get(k);
  }
  has(k) {
    return this._map.has(k);
  }
  delete(k) {
    if (this._map.delete(k)) {
      this._keys.splice(this._keys.indexOf(k), 1);
      this.size = this._map.size;
    }
    return this;
  }
  *keys() {
    yield* this._keys;
  }
  *values() {
    for (const k of this._keys) yield this._map.get(k);
  }
  *entries() {
    for (const k of this._keys) yield [k, this._map.get(k)];
  }
  forEach(f) {
    for (const k of this._keys) f(this._map.get(k), k, this);
  }
  clear() {
    this._map.clear();
    this._keys = [];
    this.size = 0;
  }
  [Symbol.iterator]() {
    return this.entries();
  }
}
);

export function sorted_map(...kvs) {
  const m = new SortedMap();
  for (let i = 0; i < kvs.length; i += 2) m.set(kvs[i], kvs[i + 1]);
  return m;
}

export function sorted_map_by(cmp, ...kvs) {
  const m = new SortedMap(null, fnToComparator(__toFn(cmp)));
  for (let i = 0; i < kvs.length; i += 2) m.set(kvs[i], kvs[i + 1]);
  return m;
}

function mkBoundFn(_sc, test, key) {
  return (e) => {
    return test(compare(e, key), 0);
  };
}

function indexFrom(sc, startKey, _asc = true) {
  let i = 0;
  for (; i < sc.length; i++) {
    if (!(compare(startKey, sc[i]) > 0)) {
      break;
    }
  }
  return i;
}

function subseq3([sc, test, key]) {
  const includeFn = mkBoundFn(sc, test, key);
  if (test === _GT_ || test === _GT__EQ_) {
    const seqFrom = [...sc];
    const startIdx = indexFrom(seqFrom, key, true);
    // delete startIdx items from the start;
    seqFrom.splice(0, startIdx);
    if (includeFn(seqFrom[0])) {
      return seqFrom;
    } else {
      // delete 1 item from the start;
      seqFrom.splice(0, 1);
      return seqFrom;
    }
  } else {
    return [...take_while(includeFn, sc)];
  }
}

function subseq5([sc, startTest, startKey, endTest, endKey]) {
  const seqFrom = [...sc];
  const startIdx = indexFrom(seqFrom, startKey, true);
  // delete startIdx items from the start
  seqFrom.splice(0, startIdx);
  const whileFn = mkBoundFn(sc, endTest, endKey);
  if (!mkBoundFn(sc, startTest, startKey)(seqFrom[0])) {
    // delete 1 item from the start
    seqFrom.splice(0, 1);
  }
  return [...take_while(whileFn, seqFrom)];
}

export function subseq(...xs) {
  if (xs.length === 3) {
    return subseq3(xs);
  }
  if (xs.length === 5) {
    return subseq5(xs);
  }
}

export function abs(x) {
  return Math.abs(x);
}

export function long$(x) {
  return fix(x);
}

export function float$(x) {
  return x;
}

export function double$(x) {
  return x;
}

export function num(x) {
  return x;
}

export function byte$(x) {
  return x;
}

export function short$(x) {
  return x;
}

export function type(x) {
  return x != null && x.constructor;
}

function preserving_reduced(rf) {
  return (a1, a2) => {
    const ret = rf(a1, a2);
    if (reduced_QMARK_(ret)) {
      return reduced(ret);
    } else return ret;
  };
}

export function cat(rf) {
  const rrf = preserving_reduced(rf);
  return (...args) => {
    switch (args.length) {
      case 0:
        return rf();
      case 1:
        return rf(args[0]);
      case 2:
        return reduce(rrf, args[0], args[1]);
    }
  };
}

export function rem(n, d) {
  const q = quot(n, d);
  return n - d * q;
}

export function memoize(f) {
  const cache = new Map();
  return (...xs) => {
    const path = [xs.length, ...xs];
    const res = get_in(cache, path);
    if (res === undefined) {
      const v = f(...xs);
      assoc_in_BANG_(cache, path, v);
      return v;
    } else return res;
  };
}

export function peek(vec) {
  if (vec == null) return null;
  // A list peeks at its front; squint lists are array-backed, so check list
  // before array to avoid returning the last element.
  if (list_QMARK_(vec)) {
    return first(vec);
  } else if (array_QMARK_(vec)) {
    return vec[vec.length - 1];
  } else if (vec[IStack__peek] !== undefined) {
    return vec[IStack__peek](vec);
  } else {
    throw missing_protocol('IStack.-peek', vec);
  }
}

export function pop(vec) {
  if (vec == null) return null;
  if (list_QMARK_(vec)) {
    if (vec.length === 0) throw new Error("Can't pop empty list");
    return rest(vec);
  } else if (array_QMARK_(vec)) {
    if (vec.length === 0) throw new Error("Can't pop empty vector");
    const ret = [...vec];
    ret.pop();
    return ret;
  } else if (vec[IStack__pop] !== undefined) {
    return vec[IStack__pop](vec);
  } else {
    throw missing_protocol('IStack.-pop', vec);
  }
}

export function pop_BANG_(v) {
  if (v != null && isVectorArray(v)) {
    if (v.length === 0) throw new Error("Can't pop empty vector");
    v.pop();
    return v;
  }
  if (v != null && v[ITransientVector__pop_BANG_] !== undefined) {
    return v[ITransientVector__pop_BANG_](v);
  }
  throw missing_protocol('ITransientVector.-pop!', v);
}

export function update_keys(m, f) {
  const m2 = empty(m);
  if (m2 != null && m2[IAssociative__assoc] !== undefined) {
    return reduce_kv((acc, k, v) => acc[IAssociative__assoc](acc, f(k), v), m2, m);
  }
  const assocFn = getAssocMut(m) || assoc_BANG_;
  reduce_kv(
    (acc, k, v) => {
      return assocFn(acc, f(k), v);
    },
    m2,
    m
  );
  return m2;
}

export function update_vals(m, f) {
  const m2 = empty(m);
  if (m2 != null && m2[IAssociative__assoc] !== undefined) {
    return reduce_kv((acc, k, v) => acc[IAssociative__assoc](acc, k, f(v)), m2, m);
  }
  const assocFn = getAssocMut(m) || assoc_BANG_;
  reduce_kv(
    (acc, k, v) => {
      return assocFn(acc, k, f(v));
    },
    m2,
    m
  );
  return m2;
}

export function random_uuid() {
  return new UUID(crypto.randomUUID());
}

export class UUID {
  constructor(uuid) {
    this.uuid = uuid;
  }
  toString() {
    return this.uuid;
  }
}

export function uuid(s) {
  // lowercased, like CLJS
  return new UUID(s.toLowerCase());
}

export function uuid_QMARK_(x) {
  return x instanceof UUID;
}

const UUID_REGEX = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

export function parse_uuid(s) {
  if (typeof s !== 'string') {
    throw new Error('Expected string, got: ' + (s === null ? 'nil' : typeof s));
  }
  return UUID_REGEX.test(s) ? uuid(s) : null;
}

export function inst_QMARK_(x) {
  return x instanceof Date;
}

const DELAY_DEREF = (self) => self._deref();

export class Delay {
  constructor(f) {
    this.f = f;
    this[IDEREF_SYM] = true;
    this[IDeref__deref] = DELAY_DEREF;
  }
  _deref() {
    if (this.realized) {
      return this.v;
    } else {
      this.v = this.f();
      this.realized = true;
      return this.v;
    }
  }
}

export function realized_QMARK_(x) {
  if (x instanceof Delay || x instanceof LazyIterable) {
    return x.realized === true;
  }
  throw new Error('realized? not supported on: ' + str(x));
}

export function force(x) {
  return x instanceof Delay ? x._deref() : x;
}

function clj__GT_js_(x, seen) {
  // keywords lower to their name string, like CLJS clj->js
  if (isKw(x)) return String(x);
  // we need to protect against circular objects
  if (seen.has(x)) return x;
  seen.add(x);
  if (x != null && x[IEncodeJS__clj__GT_js] !== undefined) {
    return x[IEncodeJS__clj__GT_js](x, (v) => clj__GT_js_(v, seen));
  }
  if (map_QMARK_(x)) {
    return update_vals(x, (x) => clj__GT_js_(x, seen));
  }

  const tc = typeConst(x);
  if (tc && tc != OBJECT_TYPE && tc != INSTANCE_TYPE) {
    return mapv((x) => clj__GT_js_(x, seen), x);
  }
  return x;
}

export function clj__GT_js(x) {
  return clj__GT_js_(x, new Set());
}

export function run_BANG_(proc, coll) {
  reduce((_, x) => proc(x), null, coll);
}

export function not_EQ_(...more) {
  return not(_EQ_(...more));
}

const VOLATILE_DEREF = (self) => self.v;

class Volatile {
  constructor(v) {
    this.v = v;
    this[IDeref__deref] = VOLATILE_DEREF;
  }
}

export function volatile_BANG_(x) {
  return new Volatile(x);
}

export function vreset_BANG_(vol, v) {
  vol.v = v;
  return v;
}

// readably false: strings unquoted
function toEDN(value, seen = new WeakSet(), readably = true) {
  if (value == null) return 'nil';
  if (typeof value === 'number') {
    if (value === Infinity) return '##Inf';
    if (value === -Infinity) return '##-Inf';
    if (Number.isNaN(value)) return '##NaN';
    return String(value);
  }
  if (typeof value === 'boolean') return String(value);
  if (typeof value === 'string') return readably ? JSON.stringify(value) : value;
  if (typeof value === 'bigint') return `${value}N`;

  if (typeof value === 'object') {
    if (value instanceof UUID) return readably ? `#uuid "${value.uuid}"` : value.uuid;
    // seen tracks the current ancestor path only. A shared reference that
    // appears under sibling branches is a DAG, not a cycle, so delete on exit.
    if (seen.has(value)) return '#object[circular]';
    seen.add(value);
    const T = typeConst(value);
    let keys, result;
    switch (T) {
      case ARRAY_TYPE:
        result = `[${value.map((v) => toEDN(v, seen, readably)).join(' ')}]`;
        break;
      case SET_TYPE:
        result = `#{${Array.from(value)
          .map((v) => toEDN(v, seen, readably))
          .join(' ')}}`;
        break;
      case MAP_TYPE:
        result = `#js/Map {${Array.from(value.entries())
          .map(([k, v]) => `${toEDN(k, seen, readably)} ${toEDN(v, seen, readably)}`)
          .join(', ')}}`;
        break;
      case LAZY_ITERABLE_TYPE:
      case LIST_TYPE:
        result = `(${mapv((v) => `${toEDN(v, seen, readably)}`, value).join(', ')})`;
        break;
      default:
        if (value[IPrintWithWriter__pr_writer] !== undefined) {
          let buf = '';
          const writer = { [IWriter__write]: (_w, s) => (buf += s), [IWriter.__sym]: true };
          value[IPrintWithWriter__pr_writer](value, writer, { readably: readably });
          result = buf;
          break;
        }
        if (value[IRecord.__sym] !== undefined) {
          keys = Object.keys(value);
          result = `#${value.constructor.name}{${keys.map((k) => `:${k} ${toEDN(value[k], seen, readably)}`).join(', ')}}`;
          break;
        }
        // Non-plain objects (Promise, Error, Date, class instances, ...) have a
        // constructor other than Object. Print them as #<Name> rather than {}
        // (which is what Object.keys would yield for opaque values like a
        // Promise).
        if (value.constructor && value.constructor !== Object) {
          seen.delete(value);
          return `#<${value.constructor.name}>`;
        }
        keys = Object.keys(value);
        result = `{${keys.map((k) => `:${k} ${toEDN(value[k], seen, readably)}`).join(', ')}}`;
    }
    seen.delete(value);
    return result;
  }

  return `#object[${value.constructor.name}]`;
}

export function pr_str(...xs) {
  return xs.map((v, _) => toEDN(v)).join(' ');
}

export function prn(...xs) {
  _STAR_print_fn_STAR_.val(pr_str(...xs));
  if (_STAR_print_newline_STAR_.val) _STAR_print_fn_STAR_.val('\n');
}

export function prn_str(...xs) {
  return pr_str(...xs) + '\n';
}

