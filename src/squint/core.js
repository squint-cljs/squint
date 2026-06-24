/*eslint no-unused-vars: ["error", { "varsIgnorePattern": "^_", "argsIgnorePattern": "^_", "destructuredArrayIgnorePattern": "^_"}]*/

// __toFn is not public API - the leading underscores mark it as an
// implementation helper shared with other squint runtime modules
// (e.g. multi.js). Signature and semantics may change without notice.
export function __toFn(x) {
  if (x == null || typeof x === 'function') return x;
  const t = typeof x;
  if (t === 'string') return (coll, d) => get(coll, x, d);
  if (t === 'object') return (k, d) => get(x, k, d);
  return x;
}

// inlined and modified version of https://github.com/lukeed/dequal
var has = Object.prototype.hasOwnProperty;

function findKey(iter, tar, key) {
  for (key of iter.keys()) {
    if (dequal(key, tar)) return key;
  }
}

function dequal(foo, bar) {
  // supports primitives, Array, Set, Map and plain objects
  // like CLJS: does not support NaN
  if (foo === bar) return true;
  // null and undefined are both nil in CLJS, so they compare equal
  if (foo == null) return bar == null;
  var ctor, len, tmp;

  if (foo && bar && (ctor = foo.constructor) === bar.constructor) {
    if (ctor === Array) {
      if ((len = foo.length) === bar.length) {
        while (len-- && dequal(foo[len], bar[len]));
      }
      return len === -1;
    }

    if (ctor === Set) {
      if (foo.size !== bar.size) {
        return false;
      }
      for (const elt of foo) {
        tmp = elt;
        if (tmp && typeof tmp === 'object') {
          tmp = findKey(bar, tmp);
          if (!tmp) return false;
        }
        if (!bar.has(tmp)) return false;
      }
      return true;
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
      return objAssocMut;
  }
}

export function assoc_BANG_(m, k, v, ...kvs) {
  if (kvs.length % 2 !== 0) {
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

const _metaSym = Symbol('meta');

// Copies metadata from `from` onto `to` (when present) and returns `to`. Used
// to make value-producing operations (copy, empty, conj, into) carry metadata
// like Clojure does, instead of dropping it on the freshly built structure.
// Kept branch-light for the hot path: reads an absent symbol property (cheap,
// no hidden-class change) and only writes when metadata is actually present.
function copyMeta(from, to) {
  const m = from?.[_metaSym];
  if (m !== undefined) to[_metaSym] = m;
  return to;
}

function copy(o) {
  switch (typeConst(o)) {
    case MAP_TYPE:
      return copyMeta(o, new Map(o));
    case SET_TYPE:
      return copyMeta(o, new o.constructor(o));
    case ARRAY_TYPE:
      return copyMeta(o, [...o]);
    case OBJECT_TYPE:
      return copyMeta(o, { ...o });
    case LIST_TYPE:
      return copyMeta(o, new List(...o));
    default:
      throw new Error(`Don't know how to copy object of type ${typeof o}.`);
  }
}

export function assoc(o, k, v, ...kvs) {
  if (!o) {
    o = {};
  }
  const ret = copy(o);
  assoc_BANG_(ret, k, v, ...kvs);
  return ret;
}

const MAP_TYPE = 1;
const ARRAY_TYPE = 2;
const OBJECT_TYPE = 3;
const LIST_TYPE = 4;
const SET_TYPE = 5;
const LAZY_ITERABLE_TYPE = 6;

// type tag set in each collection ctor, read by typeConst (DCE: no instanceof).
const TYPE_TAG = Symbol('squint.lang.type');

// @__NO_SIDE_EFFECTS__ lets bundlers drop unused calls, so a computed-key class
// or IApply fn shakes out (neither does alone). See doc/dev/dce.md.
// @__NO_SIDE_EFFECTS__
function defclass(c) {
  return c;
}
// @__NO_SIDE_EFFECTS__
function withApply(f, applyFn) {
  f[IApply__apply] = applyFn;
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
  if (tag !== undefined) return tag;
  if (isVectorArray(obj)) return ARRAY_TYPE;

  // everything more specific than Object should go before this
  if (obj instanceof Object) return OBJECT_TYPE;

  return undefined;
}

function assoc_in_with(f, fname, o, keys, value) {
  keys = vec(keys);
  o = o || {}; // default nil behavior is JS object
  const baseType = typeConst(o);
  if (baseType !== MAP_TYPE && baseType !== ARRAY_TYPE && baseType !== OBJECT_TYPE)
    throw new Error(
      `Illegal argument: ${fname} expects the first argument to be a Map, Array, or Object.`
    );

  const chain = [o];
  let lastInChain = o;

  for (let i = 0; i < keys.length - 1; i += 1) {
    const k = keys[i];
    let chainValue;
    if (lastInChain instanceof Map) chainValue = lastInChain.get(k);
    else chainValue = lastInChain[k];
    if (!chainValue) {
      chainValue = emptyOfType(baseType);
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
  if (xs.length === 0) {
    return vector();
  }

  const [_o, ...rest] = xs;

  let o = _o;
  if (o === null || o === undefined) {
    o = [];
  }

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
        if (!Array.isArray(x))
          iterable(x).forEach((kv) => {
            o.set(kv[0], kv[1]);
          });
        else o.set(x[0], x[1]);
      }
      break;
    case OBJECT_TYPE:
      for (const x of rest) {
        if (!Array.isArray(x)) Object.assign(o, x);
        else o[x[0]] = x[1];
      }
      break;
    default:
      throw new Error(
        'Illegal argument: conj! expects a Set, Array, List, Map, or Object as the first argument.'
      );
  }

  return o;
}

export function conj(...xs) {
  if (xs.length === 0) {
    return vector();
  }

  const [_o, ...rest] = xs;

  let o = _o;
  if (o === null || o === undefined) {
    o = list();
  }
  let m, o2;

  switch (typeConst(o)) {
    case SET_TYPE:
      if (o instanceof SortedSet) {
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
        if (!Array.isArray(x))
          iterable(x).forEach((kv) => {
            m.set(kv[0], kv[1]);
          });
        else m.set(x[0], x[1]);
      }

      return copyMeta(o, m);
    case LAZY_ITERABLE_TYPE:
      return lazy(function* () {
        yield* rest;
        yield* o;
      });
    case OBJECT_TYPE:
      o2 = { ...o };

      for (const x of rest) {
        if (!Array.isArray(x)) Object.assign(o2, x);
        else o2[x[0]] = x[1];
      }

      return copyMeta(o, o2);
    default:
      throw new Error(
        'Illegal argument: conj expects a Set, Array, List, Map, or Object as the first argument.'
      );
  }
}

export function disj_BANG_(s, ...xs) {
  for (const x of xs) {
    s.delete(x);
  }
  return s;
}

export function disj(s, ...xs) {
  if (s == null) return s;
  const s1 = new s.constructor([...s]);
  return disj_BANG_(s1, ...xs);
}

export function contains_QMARK_(coll, v) {
  switch (typeConst(coll)) {
    case SET_TYPE:
    case MAP_TYPE:
      return coll.has(v);
    case undefined:
      return false;
    default:
      return v in coll;
  }
}

export function dissoc_BANG_(m, ...ks) {
  for (const k of ks) {
    delete m[k];
  }

  return m;
}

export function dissoc(m, ...ks) {
  if (!m) return;
  if (ks.length === 0) return m;
  const m2 = copy(m);
  switch (typeConst(m)) {
    case MAP_TYPE:
      for (const k of ks) {
        m2.delete(k);
      }
      break;
    default:
      for (const k of ks) {
        delete m2[k];
      }
      break;
  }
  return m2;
}

export function inc(n) {
  return n + 1;
}

export function dec(n) {
  return n - 1;
}

export function println(...args) {
  console.log(...args);
}

export function nth(coll, idx, orElse) {
  const hasDefault = arguments.length > 2;
  // nil coll puns to nil, like Clojure
  if (coll == null) return hasDefault ? orElse : null;
  // "found" is decided by the index bound, not the value. An in-bounds element
  // that happens to be undefined is still found.
  if (Array.isArray(coll)) {
    if (idx >= 0 && idx < coll.length) {
      return coll[idx];
    }
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
    case SET_TYPE:
      if (coll.has(key)) v = key;
      break;
    case MAP_TYPE:
      v = coll.get(key);
      break;
    case ARRAY_TYPE:
      v = coll[key];
      break;
    default:
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

export const sequential_QMARK_ = seq_QMARK_;

export function seqable_QMARK_(x) {
  return (
    x === null ||
    x === undefined ||
    // plain objects (squint maps) are seqable via Object.entries in `iterable`,
    // even though they lack Symbol.iterator.
    object_QMARK_(x) ||
    // we used to check instanceof Object but this returns false for TC39 Records
    // also we used to write `Symbol.iterator in` but this does not work for strings and some other types
    !!x[Symbol.iterator]
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
  if (x instanceof Object) return Object.entries(x).map(tagMapEntry);
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
  const iter = iterable(x);
  // return nil for terminal checking
  if (iter.length === 0 || iter.size === 0) {
    return null;
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

class Reduced {
  value;
  constructor(x) {
    this.value = x;
  }
  _deref() {
    return this.value;
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
  // CLJS supports (.indexOf coll x) on seqs; mirror it so lazy seqs work like
  // arrays. Uses value equality, like cljs.core, and returns -1 when absent.
  indexOf(x, fromIndex = 0) {
    let i = 0;
    for (const v of this) {
      if (i >= fromIndex && dequal(v, x)) return i;
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

export class Atom {
  constructor(init) {
    this.val = init;
    this._watches = {};
    this._deref = () => this.val;
    this._hasWatches = false;
    this._reset_BANG_ = (x) => {
      const old_val = this.val;
      this.val = x;
      if (this._hasWatches) {
        for (const entry of Object.entries(this._watches)) {
          const k = entry[0];
          const f = entry[1];
          f(k, this, old_val, x);
        }
      }
      return x;
    };
    this._add_watch = (k, fn) => {
      this._watches[k] = fn;
      this._hasWatches = true;
    };
    this._remove_watch = (k) => {
      delete this._watches[k];
    };
  }
}

export function atom(init) {
  return new Atom(init);
}

export function deref(ref) {
  return ref._deref();
}

export function reset_BANG_(atm, v) {
  atm._reset_BANG_(v);
}

export function swap_BANG_(atm, f, ...args) {
  f = __toFn(f);
  const v = f(deref(atm), ...args);
  reset_BANG_(atm, v);
  return v;
}

export function swap_vals_BANG_(atm, f, ...args) {
  const oldv = deref(atm);
  f = __toFn(f);
  const newv = f(oldv, ...args);
  atm._reset_BANG_(newv);
  return [oldv, newv];
}

export function reset_vals_BANG_(atm, newv) {
  const oldv = deref(atm);
  atm._reset_BANG_(newv);
  return [oldv, newv];
}

export function compare_and_set_BANG_(atm, oldv, newv) {
  if (deref(atm) === oldv) {
    atm._reset_BANG_(newv);
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
  return arr.slice(start, end);
}

export function vector(...args) {
  return args;
}

export const array = vector;

export function vector_QMARK_(x) {
  return isVectorArray(x);
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
  return pushAll([], x);
}

export function set(coll) {
  return new Set(iterable(coll));
}

export function set_QMARK_(x) {
  return typeConst(x) === SET_TYPE;
}

const IApply__apply = Symbol('IApply__apply');

export function apply(f, ...args) {
  f = __toFn(f);
  const xs = args.slice(0, args.length - 1);
  const coll = iterable(args[args.length - 1]);
  const af = f[IApply__apply];
  if (af) {
    return af(...xs, coll);
  }
  return f(...xs, ...coll);
}

export function even_QMARK_(x) {
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
  return typeConst(x) === LIST_TYPE;
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
  // ret could be object or array, but in the future, maybe we'll have an IEmpty protocol
  const ret = emptyOfType(type) || {};
  for (const k of ks) {
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
  let step = n,
    pad = [],
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
      } else if (pad.length) {
        p.push(...pad.slice(0, n - p.length));
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
    return copyMeta(coll, emptyOfType(type));
  } else {
    throw new Error(`Can't create empty of ${typeof coll}`);
  }
}

export function merge(...args) {
  // if the first arg is nil we coerce it into a map.
  const firstArg = args[0];
  let obj;
  if (firstArg === null || firstArg === undefined) {
    obj = {};
  } else {
    obj = into(empty(firstArg), firstArg);
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
      return reduce(conj_BANG_, copy(to), args[1]);
    case 3:
      to = args[0];
      xform = args[1];
      from = args[2];
      c = copy(to);
      rf = (coll, v) => {
        if (v === undefined) {
          return coll;
        }
        return conj_BANG_(coll, v);
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

  return {
    [IIterable]: true,
    [IIterable__iterator]:
      args.length == 1
        ? function* () {
            const x = args[0];
            while (true) yield x;
          }
        : function* () {
            const [n, x] = args;
            for (var i = 0; i < n; i++) yield x;
          },
  };
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

export function take(n, coll) {
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
  return lazy(function* () {
    while (true) yield* coll;
  });
}

function drop1(n) {
  return transducer((rf) => {
    let na = n;
    return (r, x) => (na-- > 0 ? r : rf(r, x));
  });
}

export function drop(n, xs) {
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
  for (const item of path) {
    entry = get(entry, item);
  }
  if (entry === undefined) return orElse;
  return entry;
}

export function update_in(coll, path, f, ...args) {
  f = __toFn(f);
  return assoc_in(coll, path, f(get_in(coll, path), ...args));
}

export function fnil(f, x, ...xs) {
  f = __toFn(f);
  return function (a, ...args) {
    if (!a) {
      return f(x, ...xs, ...args);
    } else {
      return f(a, ...xs, ...args);
    }
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
  if (n) {
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
  return !!x;
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
  // realize as concrete array
  return vec(x);
}

export function aclone(arr) {
  const cloned = [...arr];
  return cloned;
}

export function add_watch(ref, key, fn) {
  return ref._add_watch(key, fn);
}

export function remove_watch(ref, key) {
  return ref._remove_watch(key);
}

export function reduce_kv(f, init, m) {
  if (!m) {
    return init;
  }
  var ret = init;
  for (const o of iterable(m)) {
    ret = f(ret, o[0], o[1]);
  }
  return ret;
}

export function max(x, y, ...more) {
  if (y == undefined) {
    return x;
  }
  return Math.max(x, y, ...more);
}

export function min(x, y, ...more) {
  if (y == undefined) {
    return x;
  }
  return Math.min(x, y, ...more);
}

export function map_QMARK_(coll) {
  if (coll == null) return false;
  if (isObj(coll)) return true;
  if (coll instanceof Map) return true;
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
    for (const f of fns) {
      for (const a of args) {
        const res = f(a);
        if (res) {
          return res;
        }
      }
    }
    return undefined;
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
  return Number.isNaN(x);
}

export function number_QMARK_(x) {
  return typeof x == 'number';
}

export function keys(obj) {
  if (obj == null) return;
  const t = typeConst(obj);
  switch (t) {
    case OBJECT_TYPE:
      return Object.keys(obj);
    case MAP_TYPE:
      return Array.from(obj.keys());
  }
}

export function js_keys(obj) {
  return keys(obj);
}

export function vals(obj) {
  if (obj == null) return;
  const t = typeConst(obj);
  switch (t) {
    case OBJECT_TYPE:
      return Object.values(obj);
    case MAP_TYPE:
      return Array.from(obj.values());
  }
}

export function string_QMARK_(s) {
  return typeof s === 'string';
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

// squint has no keyword type; keywords are strings, so keyword/keyword? are
// string-based. (keyword name) or (keyword ns name).
export function keyword(arg1, arg2) {
  if (arg2 !== undefined) {
    return (arg1 != null ? arg1 + '/' : '') + arg2;
  }
  return arg1;
}

export function keyword_QMARK_(x) {
  return typeof x === 'string';
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
  return typeof x === 'string' && !x.includes('/');
}

export function qualified_keyword_QMARK_(x) {
  return typeof x === 'string' && x.includes('/');
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
  if (x instanceof Object) {
    return x[_metaSym];
  } else return null;
}

export function with_meta(x, m) {
  // For functions, wrap in a new callable that forwards to the original
  // so fn? stays true and the original isn't mutated. copy() can't handle
  // functions - a {...x} spread loses the call signature.
  if (typeof x === 'function') {
    const wrapped = function (...args) { return x.apply(this, args); };
    wrapped[_metaSym] = m;
    return wrapped;
  }
  const ret = copy(x);
  ret[_metaSym] = m;
  return ret;
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
  if (more.length == 0) {
    return x;
  }
  var kx = k(x);
  var min = x;
  more.forEach((y) => {
    var ky = k(y);
    if (ky <= kx) {
      kx = ky;
      min = y;
    }
  });
  return min;
}

export function max_key(k, x, ...more) {
  if (more.length == 0) {
    return x;
  }
  var kx = k(x);
  var max = x;
  more.forEach((y) => {
    var ky = k(y);
    if (ky >= kx) {
      kx = ky;
      max = y;
    }
  });
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
  return copy(x);
}

export function persistent_BANG_(x) {
  // no Object.freeze: persistent structures stay extensible so symbol-keyed
  // metadata can be attached
  return x;
}

const SortedSet = defclass(
class SortedSet {
  constructor(xs) {
    this[TYPE_TAG] = SET_TYPE;
    const isSorted = xs instanceof SortedSet;
    if (!isSorted) {
      xs = sort(xs);
    }
    const s = new Set(xs);
    // we don't re-use xs since xs can contain duplicates
    this._elts = [...s];
    this._set = s;
  }
  add(x) {
    if (this._set.has(x)) return this;
    const xs = this._elts;
    let added = false;
    for (let i = 0; i < xs.length; i++) {
      if (compare(x, xs[i]) <= 0) {
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
  rf = preserving_reduced(rf);
  return (...args) => {
    switch (args.length) {
      case 0:
        return rf();
      case 1:
        return rf(args[0]);
      case 2:
        return reduce(rf, args[0], args[1]);
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
  if (array_QMARK_(vec)) {
    return vec[vec.length - 1];
  } else {
    return first(vec);
  }
}

export function pop(vec) {
  if (array_QMARK_(vec)) {
    const ret = [...vec];
    ret.pop();
    return ret;
  } else {
    return rest(vec);
  }
}

export function update_keys(m, f) {
  const m2 = empty(m);
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
  return crypto.randomUUID();
}

export class Delay {
  constructor(f) {
    this.f = f;
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

function clj__GT_js_(x, seen) {
  // we need to protect against circular objects
  if (seen.has(x)) return x;
  seen.add(x);
  if (map_QMARK_(x)) {
    return update_vals(x, (x) => clj__GT_js_(x, seen));
  }

  const tc = typeConst(x);
  if (tc && tc != OBJECT_TYPE) {
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

class Volatile {
  constructor(v) {
    this.v = v;
  }
  _deref() {
    return this.v;
  }
}

export function volatile_BANG_(x) {
  return new Volatile(x);
}

export function vreset_BANG_(vol, v) {
  vol.v = v;
  return v;
}

function toEDN(value, seen = new WeakSet()) {
  if (value == null) return 'nil';
  if (typeof value === 'number') {
    if (value === Infinity) return '##Inf';
    if (value === -Infinity) return '##-Inf';
    if (Number.isNaN(value)) return '##NaN';
    return String(value);
  }
  if (typeof value === 'boolean') return String(value);
  if (typeof value === 'string') return JSON.stringify(value);
  if (typeof value === 'bigint') return `${value}N`;

  if (typeof value === 'object') {
    // seen tracks the current ancestor path only. A shared reference that
    // appears under sibling branches is a DAG, not a cycle, so delete on exit.
    if (seen.has(value)) return '#object[circular]';
    seen.add(value);
    const T = typeConst(value);
    let keys, result;
    switch (T) {
      case ARRAY_TYPE:
        result = `[${value.map((v) => toEDN(v, seen)).join(' ')}]`;
        break;
      case SET_TYPE:
        result = `#{${Array.from(value)
          .map((v) => toEDN(v, seen))
          .join(' ')}}`;
        break;
      case MAP_TYPE:
        result = `#js/Map {${Array.from(value.entries())
          .map(([k, v]) => `${toEDN(k, seen)} ${toEDN(v, seen)}`)
          .join(', ')}}`;
        break;
      case LAZY_ITERABLE_TYPE:
      case LIST_TYPE:
        result = `(${mapv((v) => `${toEDN(v, seen)}`, value).join(', ')})`;
        break;
      default:
        // Non-plain objects (Promise, Error, Date, class instances, ...) have a
        // constructor other than Object. Print them as #<Name> rather than {}
        // (which is what Object.keys would yield for opaque values like a
        // Promise).
        if (value.constructor && value.constructor !== Object) {
          seen.delete(value);
          return `#<${value.constructor.name}>`;
        }
        keys = Object.keys(value);
        result = `{${keys.map((k) => `:${k} ${toEDN(value[k], seen)}`).join(', ')}}`;
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
  return console.log(pr_str(...xs));
}
