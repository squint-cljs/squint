/*eslint no-unused-vars: ["error", { "varsIgnorePattern": "^_", "argsIgnorePattern": "^_", "destructuredArrayIgnorePattern": "^_"}]*/

function toFn(x) {
  if (x == null) return x;
  if (x instanceof Function) {
    return x;
  }
  const t = typeof x;
  if (t === 'string') {
    return (coll, d) => {
      return get(coll, x, d);
    };
  }
  if (t === 'object') {
    return (k, d) => {
      return get(x, k, d);
    };
  }
  return x;
}

function walkArray(arr, comp) {
  return arr.every(function (x, i) {
    return i === 0 || comp(arr[i - 1], x);
  });
}

export function _EQ_(...xs) {
  return walkArray(xs, (x, y) => x === y);
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

export function satisfies_QMARK_(protocol, x) {
  return x[protocol];
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
    case MAP_TYPE: return mapAssocMut;
    case ARRAY_TYPE:
    case OBJECT_TYPE: return objAssocMut;
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

function copy(o) {
  switch (typeConst(o)) {
    case MAP_TYPE:
      return new Map(o);
    case SET_TYPE:
      return new o.constructor(o);
    case ARRAY_TYPE:
      return [...o];
    case OBJECT_TYPE:
      return { ...o };
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
  if (obj instanceof List) return LIST_TYPE;
  if (Array.isArray(obj)) return ARRAY_TYPE;
  if (obj instanceof LazyIterable) return LAZY_ITERABLE_TYPE;
  if (obj instanceof SortedSet) return SET_TYPE;

  // everything more specific than Object should go before this
  if (obj instanceof Object) return OBJECT_TYPE;

  return undefined;
}

function assoc_in_with(f, fname, o, keys, value) {
  keys = vec(keys);
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
  fs = fs.map(toFn);
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
        return conj_BANG_set(new o.constructor(o), rest);
      } else {
        return new o.constructor([...o, ...rest]);
      }
    case LIST_TYPE:
      return new List(...rest.reverse(), ...o);
    case ARRAY_TYPE:
      return [...o, ...rest];
    case MAP_TYPE:
      m = new Map(o);
      for (const x of rest) {
        if (!Array.isArray(x))
          iterable(x).forEach((kv) => {
            m.set(kv[0], kv[1]);
          });
        else m.set(x[0], x[1]);
      }

      return m;
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

      return o2;
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
  if (coll) {
    var elt = undefined;
    if (Array.isArray(coll)) {
      elt = coll[idx];
    } else {
      const iter = iterable(coll);
      let i = 0;
      for (const value of iter) {
        if (i++ == idx) {
          elt = value;
          break;
        }
      }
    }
    if (elt !== undefined) {
      return elt;
    }
  }
  return orElse;
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
      if (g instanceof Function) {
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
    // we used to check instanceof Object but this returns false for TC39 Records
    // also we used to write `Symbol.iterator in` but this does not work for strings and some other types
    !!x[Symbol.iterator]);
}

export function iterable(x) {
  // nil puns to empty iterable, support passing nil to first/rest/reduce, etc.
  if (x === null || x === undefined) {
    return [];
  }
  if (seqable_QMARK_(x)) {
    return x;
  }
  if (x instanceof Object) return Object.entries(x);
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
  // destructuring uses iterable protocol
  const [first] = iterable(coll);
  return first;
}

export function second(coll) {
  const [_, v] = iterable(coll);
  return v;
}

export function ffirst(coll) {
  return first(first(coll));
}

export function rest(coll) {
  return lazy(function* () {
    let first = true;
    for (const x of iterable(coll)) {
      if (first) first = false;
      else yield x;
    }
  });
}

class IDerefAsync {
  then(resolve,_reject) {
    return resolve(this._deref());
  }
}

class Reduced extends IDerefAsync {
  value;
  constructor(x) {
    super();
    this.value = x;
  }
  _deref() {
    return this.value;
  }
}

export function last(coll) {
  coll = iterable(coll);
  let lastEl;
  switch (typeConst(coll)) {
    case ARRAY_TYPE:
      return coll[coll.length - 1];
    default:
      for (const x of coll) {
        lastEl = x;
      }
      return lastEl;
  }
}

export function reduced(x) {
  return new Reduced(x);
}

export function reduced_QMARK_(x) {
  return x instanceof Reduced;
}

export function reduce(f, arg1, arg2) {
  f = toFn(f);
  let coll, val;
  if (arguments.length === 2) {
    // (reduce f coll)
    const iter = iterable(arg1)[Symbol.iterator]();
    const vd = iter.next();
    if (vd.done) {
      val = f();
    } else {
      val = vd.value;
    }
    coll = iter;
  } else {
    // (reduce f val coll)
    val = arg1;
    coll = iterable(arg2);
  }
  if (val instanceof Reduced) {
    return val.value;
  }
  for (const x of coll) {
    val = f(val, x);
    if (val instanceof Reduced) {
      val = val.value;
      break;
    }
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
  let i = init, rst = coll;
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
  f = toFn(f);
  if (arguments.length === 2) {
    return lazy(function* () {
      yield* _reductions2(f, iterable(arg1)[Symbol.iterator]());
    });
  }
  return lazy(function* () {
    yield* _reductions3(f, arg1, iterable(arg2)[Symbol.iterator]());
  });
}

var tolr = false;
export function warn_on_lazy_reusage_BANG_() {
  tolr = true;
}

class LazyIterable {
  constructor(gen) {
    this.gen = gen;
    this.usages = 0;
  }
  [Symbol.iterator]() {
    this.usages++;
    if (this.usages >= 2 && tolr) {
      try {
        throw new Error();
      } catch (e) {
        console.warn('Re-use of lazy value', e.stack);
      }
    }
    return this.gen();
  }
}

LazyIterable.prototype[IIterable] = true; // Closure compatibility

export function lazy(f) {
  return new LazyIterable(f);
}

export class Cons {
  constructor(x, coll) {
    this.x = x;
    this.coll = coll;
  }
  *[Symbol.iterator]() {
    yield this.x;
    yield* iterable(this.coll);
  }
}

export function cons(x, coll) {
  return new Cons(x, coll);
  // return lazy(function* () {
  //   yield x;
  //   yield* iterable(coll);
  // });
}

export function map(f, ...colls) {
  // if (! (f instanceof Function)) {
  //   throw new Error(`Argument f must be a function but is ${typeof(f)}`);
  // }
  f = toFn(f);
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
      return lazy(function* () {
        for (const x of iterable(colls[0])) {
          yield f(x);
        }
      });
    default:
      return lazy(function* () {
        const iters = colls.map((coll) => es6_iterator(iterable(coll)));
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

function filter1(pred) {
  return (rf) => {
    return (...args) => {
      switch (args.length) {
        case 0: return rf();
        case 1: return rf(args[0]);
        case 2: {
          const result = args[0];
          const input = args[1];
          if (truth_(pred(input))) {
            return rf(result, input);
          } else return result;
        }
      }
    };
  };
}

export function filter(pred, coll) {
  if (arguments.length === 1) {
    return filter1(pred);
  }
  pred = toFn(pred);
  return lazy(function* () {
    for (const x of iterable(coll)) {
      if (truth_(pred(x))) {
        yield x;
      }
    }
  });
}

export function filterv(pred, coll) {
  return [...filter(pred, coll)];
}

export function remove(pred, coll) {
  return filter(complement(pred), coll);
}

function map_indexed1(f) {
  return (rf) => {
    let i = -1;
    return (...args) => {
      switch (args.length) {
        case 0: return rf();
        case 1: return rf(args[0]);
        case 2: return rf(args[0], f((i = i + 1, i), args[1]));
      }
    };
  };
}

export function map_indexed(f, coll) {
  f = toFn(f);
  if (arguments.length === 1) {
    return map_indexed1(f);
  }
  const ret = [];
  let i = 0;
  for (const x of iterable(coll)) {
    ret.push(f(i, x));
    i++;
  }
  return ret;
}

function keep_indexed2(f, coll) {
  f = toFn(f);
  const ret = [];
  let i = 0;
  for (const x of iterable(coll)) {
    const fret = f(i, x);
    if (truth_(fret)) {
      ret.push(fret);
    }
    i++;
  }
  return ret;
}

function keep_indexed1(f) {
  return (rf) => {
    let ia = -1;
    return (...args) => {
      const al = args.length;
      if (al === 0) {
        return rf();
      }
      if (al === 1) {
        return rf(args[0]);
      }
      if (al === 2) {
        const result = args[0];
        const input = args[1];
        ia++;
        const v = f(ia, input);
        if (v == null) {
          return result;
        }
        return rf(result, v);
      }
    };
  };
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

export function not(expr) {
  return !truth_(expr);
}

export function nil_QMARK_(v) {
  return v == null;
}

export const PROTOCOL_SENTINEL = {};

function pr_str_1(x) {
  if (x === null) {
    return 'null';
  }
  return JSON.stringify(x, (_key, value) => {
    switch (typeConst(value)) {
      case SET_TYPE:
      case LAZY_ITERABLE_TYPE:
        return [...value];
      case MAP_TYPE:
        return Object.fromEntries(value);
      default: {
        // console.log(value);
        return value;
      }
    }
  });
}

export function pr_str(...xs) {
  return xs.map(pr_str_1).join(' ');
}

export function prn(...xs) {
  println(pr_str(...xs));
}

export class Atom extends IDerefAsync {
  constructor(init) {
    super();
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
  f = toFn(f);
  const v = f(deref(atm), ...args);
  reset_BANG_(atm, v);
  return v;
}

export function range(begin, end, step) {
  return lazy(function* () {
    let b = begin,
      e = end,
      s = step;
    if (end === undefined) {
      b = 0;
      e = begin;
    }
    let i = b || 0;
    s = step || 1;
    while (e === undefined || i < e) {
      yield i;
      i += s;
    }
  });
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

export function vector_QMARK_(x) {
  return typeConst(x) === ARRAY_TYPE;
}

export function mapv(...args) {
  return [...map(...args)];
}

export function vec(x) {
  if (array_QMARK_(x)) {
    // return original, no need to clone the entire thing
    return x;
  }
  return [...iterable(x)];
}

export function set(coll) {
  return new Set(iterable(coll));
}

const IApply__apply = Symbol('IApply__apply');

export function apply(f, ...args) {
  f = toFn(f);
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
  f = toFn(f);
  return (...args) => not(f(...args));
}

export function constantly(x) {
  return (..._) => x;
}

class List extends Array {
  constructor(...args) {
    super();
    this.push(...args);
  }
}

export function list_QMARK_(x) {
  return typeConst(x) === LIST_TYPE;
}

export function list(...args) {
  return new List(...args);
}

export function array_QMARK_(x) {
  return Array.isArray(x);
}

function concat1(colls) {
  return lazy(function* () {
    for (const coll of colls) {
      yield* iterable(coll);
    }
  });
}

export function concat(...colls) {
  return concat1(colls);
}

// lazy seqable argument
concat[IApply__apply] = (colls) => {
  return concat1(colls);
};

export function mapcat(f, ...colls) {
  const mapped = map(f, ...colls);
  return concat1(mapped);
}

export function identity(x) {
  return x;
}

export function interleave(...colls) {
  return lazy(function* () {
    const iters = colls.map((coll) => es6_iterator(iterable(coll)));
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
  return (rf) => {
    let started = false;
    return (...args) => {
      switch (args.length) {
        case 0: return rf();
        case 1: return rf(args[0]);
        case 2: {
          if (started) {
            const sepr = rf(args[0], sep);
            if (reduced_QMARK_(sepr)) {
              return sepr;
            } else {
              return rf(sepr, args[1]);
            }
          } else {
            started = true;
            return rf(args[0], args[1]);
          }
        }
      }
    };
  };
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
    if (v != undefined) {
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
        case 0: return rf();
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
  return lazy(function* () {
    let p = [];
    let i = 0;
    for (const x of iterable(coll)) {
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
  f = toFn(f);
  if (arguments.length === 1) {
    return partition_by1(f);
  }
  return lazy(function* () {
    const iter = es6_iterator(coll);
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
    return emptyOfType(type);
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
  f = toFn(f);
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
      return conj(args[0] ?? [], ...iterable(args[1]));
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
  return (rf) => {
    let na = n;
    return (...args) => {
      const al = args.length;
      if (al === 0) {
        return rf();
      }
      if (al === 1) {
        const result = args[0];
        return rf(result);
      }
      if (al === 2) {
        let result = args[0];
        const input = args[1];
        const n = na;
        const nn = (na = na - 1, na);
        if (n > 0) {
          result = rf(result, input);
        }
        if (!(nn > 0)) {
          return ensure_reduced(result);
        }
        else {
          return result;
        }
      }
    };
  };
}

export function take(n, coll) {
  if (arguments.length === 1) {
    return take1(n);
  }
  return lazy(function* () {
    let i = n - 1;
    for (const x of iterable(coll)) {
      if (i-- >= 0) {
        yield x;
      }
      if (i < 0) {
        return;
      }
    }
  });
}

function take_while1(pred) {
  return (rf) => {
    return (...args) => {
      const al = args.length;
      if (al === 0) return rf();
      if (al === 1) return rf(args[0]);
      if (al === 2) {
        const result = args[0];
        const input = args[1];
        if (truth_(pred(input))) {
          return rf(result, input);
        } else {
          return reduced(result);
        }
      }
    };
  };
}

export function take_while(pred, coll) {
  pred = toFn(pred);
  if (arguments.length === 1) {
    return take_while1(pred);
  }
  return lazy(function* () {
    for (const o of iterable(coll)) {
      if (truth_(pred(o))) yield o;
      else return;
    }
  });
}

function take_nth1(n) {
  return (rf) => {
    let ia = -1;
    return (...args) => {
      const al = args.length;
      if (al === 0) return rf();
      if (al === 1) return rf(args[0]);
      if (al === 2) {
        const result = args[0];
        const input = args[1];
        ia++;
        const i = ia;
        if (rem(i, n) === 0) {
          return rf(result, input);
        } else return result;
      }
    };
  };
}

export function take_nth(n, coll) {
  if (arguments.length === 1) return take_nth1(n);
  if (n <= 0) {
    return repeat(first(coll));
  }

  return lazy(function* () {
    let i = 0;
    for (const x of iterable(coll)) {
      if (i % n === 0) {
        yield x;
      }
      i++;
    }
  });
}

export function partial(f, ...xs) {
  f = toFn(f);
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
  return (rf) => {
    let na = n;
    return (...args) => {
      const al = args.length;
      if (al === 0) {
        return rf();
      }
      if (al === 1) {
        return rf(args[0]);
      }
      if (al === 2) {
        const result = args[0];
        const input = args[1];
        const n = na;
        na--;
        if (n > 0) {
          return result;
        } else return rf(result, input);
      }
    };
  };
}

export function drop(n, xs) {
  if (arguments.length === 1) return drop1(n);
  return lazy(function* () {
    const iter = _iterator(iterable(xs));
    for (let x = 0; x < n; x++) {
      iter.next();
    }
    yield* iter;
  });
}

function drop_while1(pred) {
  return (rf) => {
    let da = true;
    return (...args) => {
      const al = args.length;
      if (al === 0) {
        return rf();
      }
      if (al === 1) {
        return rf(args[0]);
      }
      if (al === 2) {
        const isDrop = da;
        const result = args[0];
        const input = args[1];
        if (isDrop && truth_(pred(input))) {
          return result;
        } else {
          da = null;
          return rf(result, input);
        }
      }
    };
  };
}

export function drop_while(pred, xs) {
  pred = toFn(pred);
  if (arguments.length === 1) return drop_while1(pred);
  return lazy(function* () {
    const iter = _iterator(iterable(xs));
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
  return (rf) => {
    const seen = new Set();
    return (...args) => {
      const al = args.length;
      if (al === 0) return rf();
      if (al === 1) return rf(args[0]);
      if (al === 2) {
        const result = args[0];
        const input = args[1];
        if (seen.has(input)) return result;
        seen.add(input);
        return rf(result, input);
      }
    };
  };
}

export function distinct(coll) {
  if (arguments.length === 0) return distinct1();
  return lazy(function* () {
    const seen = new Set();
    for (const x of iterable(coll)) {
      if (!seen.has(x)) yield x;
      seen.add(x);
    }
    return;
  });
}

export function update(coll, k, f, ...args) {
  f = toFn(f);
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
  f = toFn(f);
  return assoc_in(coll, path, f(get_in(coll, path), ...args));
}

export function fnil(f, x, ...xs) {
  f = toFn(f);
  return function (a, ...args) {
    if (!a) {
      return f(x, ...xs, ...args);
    } else {
      return f(a, ...xs, ...args);
    }
  };
}

export function every_QMARK_(pred, coll) {
  pred = toFn(pred);
  for (const x of iterable(coll)) {
    if (!pred(x)) return false;
  }
  return true;
}

export function not_every_QMARK_(pred, coll) {
  return !every_QMARK_(pred, coll);
}

function keep1(pred) {
  return (rf) => {
    return (...args) => {
      const al = args.length;
      if (al === 0) return rf();
      if (al === 1) return rf(args[0]);
      if (al === 2) {
        const result = args[0];
        const input = args[1];
        const v = pred(input);
        if (v == null) return result;
        return rf(result, v);
      }
    };
  };
}

export function keep(pred, coll) {
  pred = toFn(pred);
  if (arguments.length === 1) return keep1(pred);
  return lazy(function* () {
    for (const o of iterable(coll)) {
      const res = pred(o);
      if (truth_(res)) yield res;
    }
  });
}

export function reverse(coll) {
  coll = iterable(coll);
  return [...coll].reverse();
}

export function sort(f, coll) {
  if (arguments.length === 1) {
    coll = f;
    f = undefined;
  }
  f = toFn(f);
  coll = iterable(coll);
  // we need to clone coll since .sort works in place and .toSorted isn't available on Node < 20
  const clone = [...coll];
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
  keyfn = toFn(keyfn);
  comp = toFn(comp);
  return sort((x, y) => {
    const f = fnToComparator(comp);
    const kx = keyfn(x);
    const ky = keyfn(y);
    return f(kx, ky);
  }, coll);
}

export function shuffle(coll) {
  const result = [...coll];
  let remaining = coll.length;
  while (remaining) {
    const i = Math.floor(Math.random() * remaining--);
    const tmp = result[remaining];
    result[remaining] = result[i];
    result[i] = tmp;
  }

  return result;
}

export function some(pred, coll) {
  pred = toFn(pred);
  for (const o of iterable(coll)) {
    const res = pred(o);
    if (truth_(res)) return res;
  }
  return undefined;
}

export function not_any_QMARK_(pred, coll) {
  pred = toFn(pred);
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
  const v = get(m, k);
  return assoc_BANG_(m, k, f(v, ...args));
}

export function group_by(f, coll) {
  f = toFn(f);
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

export class LazySeq {
  constructor(f) {
    this.f = f;
    this.res = undefined;
  }
  *[Symbol.iterator]() {
    if (this.res === undefined) {
      this.res = this.f();
      this.f = null;
    }
    yield* iterable(this.res);
  }
}

export function butlast(coll) {
  const x = [...iterable(coll)];
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
  let ret = 0;
  for (const _ of iterable(coll)) {
    ret++;
  }
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
  for (; ;) {
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

export function map_QMARK_(x) {
  return x instanceof Object;
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
  fs = fs.map(toFn);
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
    const tx = typeof (x);
    const ty = typeof (y);
    if (tx === 'number' && ty === 'number' || tx === 'string' && ty === 'string') {
      if (x === y) {
        return 0;
      }
      if (x < y) {
        return -1;
      }
      return 1;
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
  if (obj) {
    return Object.keys(obj);
  } else {
    return null;
  }
}

export function js_keys(obj) {
  return keys(obj);
}

export function vals(obj) {
  if (obj) {
    return Object.values(obj);
  } else {
    return null;
  }
}

export function string_QMARK_(s) {
  return typeof s === 'string';
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

export const integer_QMARK_ = int_QMARK_;

const _metaSym = Symbol('meta');

export function meta(x) {
  if (x instanceof Object) {
    return x[_metaSym];
  } else return null;
}

export function with_meta(x, m) {
  const ret = copy(x);
  ret[_metaSym] = m;
  return ret;
}

export function boolean_QMARK_(x) {
  return x === true || x === false;
}

export function counted_QMARK_(x) {
  const tc = typeConst(x);
  switch (tc) {
    case ARRAY_TYPE: case MAP_TYPE: case OBJECT_TYPE: case LIST_TYPE: case SET_TYPE:
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
    return [k, v];
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

export function parse_long(x) {
  if (string_QMARK_(x)) {
    if (/^[+-]?\d+$/.test(x)) {
      const i = parseInt(x);
      if (Number.MIN_SAFE_INTEGER <= i <= Number.MAX_SAFE_INTEGER) {
        return i;
      }
    }
    return null;
  }
  return parsing_err(x);
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
  for (; ;) {
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
  return filter(complement(sequential_QMARK_),
    rest(tree_seq(sequential_QMARK_, seq, x)));
}

export function transient$(x) {
  return copy(x);
}

export function persistent_BANG_(x) {
  return Object.freeze(x);
}

class SortedSet {
  constructor(xs) {
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
    }
    else {
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
    }
    else return ret;
  };
}

export function cat(rf) {
  rf = preserving_reduced(rf);
  return (...args) => {
    switch (args.length) {
      case 0: return rf();
      case 1: return rf(args[0]);
      case 2: return reduce(rf, args[0], args[1]);
    }
  };
}

export function rem(n, d) {
  const q = quot(n, d);
  return n - (d * q);
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
  reduce_kv((acc, k, v) => {
    return assocFn(acc, f(k), v);
  }, m2, m);
  return m2;
}

export function update_vals(m, f) {
  const m2 = empty(m);
  const assocFn = getAssocMut(m) || assoc_BANG_;
  reduce_kv((acc, k, v) => {
    return assocFn(acc, k, f(v));
  }, m2, m);
  return m2;
}
