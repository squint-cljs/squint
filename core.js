// @ts-check
export function _PLUS_(x, ...xs) {
  let sum = x;
  for (const y of xs) {
    sum += y;
  }
  return sum;
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
        'Illegal argument: assoc! expects a Map, Array, or Object as the first argument.'
      );
  }

  return m;
}

export function assoc(o, k, v, ...kvs) {
  switch (typeConst(o)) {
    case MAP_TYPE:
      return assoc_BANG_(new Map(o.entries()), k, v, ...kvs);
    case ARRAY_TYPE:
      return assoc_BANG_([...o], k, v, ...kvs);
    case OBJECT_TYPE:
      return assoc_BANG_({ ...o }, k, v, ...kvs);
    default:
      throw new Error(
        'Illegal argument: assoc expects a Map, Array, or Object as the first argument.'
      );
  }
}

const MAP_TYPE = 1;
const ARRAY_TYPE = 2;
const OBJECT_TYPE = 3;
const LIST_TYPE = 4;
const SET_TYPE = 5;

function emptyOfType(type) {
  switch (type) {
    case MAP_TYPE:
      return new Map();
    case ARRAY_TYPE:
      return [];
    case OBJECT_TYPE:
      return {};
    case LIST_TYPE:
      return new List();
    case SET_TYPE:
      return new Set();
  }
  return undefined;
}

function typeConst(obj) {
  if (obj instanceof Map) return MAP_TYPE;
  if (obj instanceof Set) return SET_TYPE;
  if (obj instanceof List) return LIST_TYPE;
  if (obj instanceof Array) return ARRAY_TYPE;
  if (obj instanceof Object) return OBJECT_TYPE;
  return undefined;
}

function assoc_in_with(f, fname, o, keys, value) {
  let baseType = typeConst(o);
  if (baseType !== MAP_TYPE && baseType !== ARRAY_TYPE && baseType !== OBJECT_TYPE)
    throw new Error(
      `Illegal argument: ${fname} expects the first argument to be a Map, Array, or Object.`
    );

  const chain = [o];
  let lastInChain = o;

  for (let i = 0; i < keys.length - 1; i += 1) {
    let k = keys[i];
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
  return assoc_in_with(assoc_BANG_, 'assoc-in!', o, keys, value);
}

export function conj_BANG_(...xs) {
  if (xs.length === 0) {
    return vector();
  }

  let [o, ...rest] = xs;

  if (o === null || o === undefined) {
    o = [];
  }

  switch (typeConst(o)) {
    case SET_TYPE:
      for (const x of rest) {
        o.add(x);
      }
      break;
    case LIST_TYPE:
      o.unshift(...rest.reverse());
      break;
    case ARRAY_TYPE:
      o.push(...rest);
      break;
    case MAP_TYPE:
      for (const x of rest) {
        if (!(x instanceof Array))
          for (const [k, v] of es6_iterator(x)) o.set(k, v);
        else o.set(x[0], x[1]);
      }
      break;
    case OBJECT_TYPE:
      for (const x of rest) {
        if (!(x instanceof Array)) Object.assign(o, x);
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

  let [o, ...rest] = xs;

  if (o === null || o === undefined) {
    o = [];
  }

  switch (typeConst(o)) {
    case SET_TYPE:
      return new Set([...o, ...rest]);
    case LIST_TYPE:
      return new List(...rest.reverse(), ...o);
    case ARRAY_TYPE:
      return [...o, ...rest];
    case MAP_TYPE:
      const m = new Map(o);

      for (const x of rest) {
        m.set(x[0], x[1]);
      }

      return m;
    case OBJECT_TYPE:
      const o2 = { ...o };

      for (const x of rest) {
        o2[x[0]] = x[1];
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
  let s1 = new Set([...s]);
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

export function dissoc_BANG_(m, k) {
  delete m[k];
  return m;
}

export function dissoc(m, k) {
  let m2 = { ...m };
  return dissoc_BANG_(m2, k);
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

export function nth(coll, idx) {
  return coll[idx];
}

export function get(coll, key, otherwise = undefined) {
  let v;
  switch (typeConst(coll)) {
    case SET_TYPE:
      if (coll.has(key)) v = key;
      break;
    case MAP_TYPE:
      v = coll.get(key);
      break;
    case undefined:
      break;
    default:
      v = coll[key];
      break;
  }
  return v !== undefined ? v : otherwise;
}

export function seqable_QMARK_(x) {
  switch (typeof x) {
    case 'object':
    case 'string':
    case 'undefined':
      return true;
    default:
      return false;
  }
}

// not public, since there is not CLJS core var name for this
function iterable(x) {
  if (!seqable_QMARK_(x))
    return undefined;
  // nil puns to empty iterable
  if (x === null || x === undefined)
    return [];
  if (typeof x === "string")
    return x;
  switch (typeConst(x)) {
    case OBJECT_TYPE:
      return Object.entries(x);
    default:
      return x;
  }
}

export function seq(x) {
  let iter = iterable(x);
  if (iter === undefined)
    throw new Error(`${x} should either implement the Symbol.iterator protocol,
                     be a POJO, or be nil`);
  if (iter.length === 0 || iter.size === 0) {
    return null;
  }
  return iter;
}

export function es6_iterator(coll) {
  // nil puns to empty iterator
  return (seq(coll) || [])[Symbol.iterator]();
}

export function first(coll) {
  // destructuring uses iterable protocol
  let [first] = es6_iterator(coll);
  return first;
}

export function second(coll) {
  let [_, v] = es6_iterator(coll);
  return v;
}

export function ffirst(coll) {
  return first(first(coll));
}

export function rest(coll) {
  let [_, ...rest] = es6_iterator(coll);
  return rest;
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

export function reduced(x) {
  return new Reduced(x);
}

export function reduced_QMARK_(x) {
  return x instanceof Reduced;
}

export function reduce(f, arg1, arg2) {
  let coll, val;
  if (arg2 === undefined) {
    // (reduce f coll)
    const [hd, ...more] = es6_iterator(arg1);
    val = hd;
    coll = more;
  } else {
    // (reduce f val coll)
    val = arg1;
    coll = es6_iterator(arg2);
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

export function map(f, ...colls) {
  const ret = [];
  switch (colls.length) {
    case 0:
      throw new Error('map with 2 arguments is not supported yet');
    case 1:
      for (const x of es6_iterator(colls[0])) {
        ret.push(f(x));
      }
      return ret;
    default:
      const iters = colls.map(es6_iterator);
      while (true) {
        let args = [];
        for (const i of iters) {
          const nextVal = i.next();
          if (nextVal.done) {
            return ret;
          }
          args.push(nextVal.value);
        }
        ret.push(f(...args));
      }
      return ret;
  }
}

export function filter(pred, coll) {
  let ret = [];
  for (const x of es6_iterator(coll)) {
    if (pred(x)) {
      ret.push(x);
    }
  }
  return ret;
}

export function map_indexed(f, coll) {
  let ret = [];
  let i = 0;
  for (const x of es6_iterator(coll)) {
    ret.push(f(i, x));
    i++;
  }
  return ret;
}

export function str(...xs) {
  return xs.join('');
}

export function not(expr) {
  return !expr;
}

export function nil_QMARK_(v) {
  return v == null;
}

export const PROTOCOL_SENTINEL = {};

function pr_str_1(x) {
  return JSON.stringify(x, (_key, value) => (value instanceof Set ? [...value] : value));
}

export function pr_str(...xs) {
  return xs.map(pr_str_1).join(' ');
}

export function prn(...xs) {
  println(pr_str(...xs));
}

export function Atom(init) {
  this.val = init;
  this._deref = () => this.val;
  this._reset_BANG_ = (x) => (this.val = x);
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
  const v = f(deref(atm), ...args);
  reset_BANG_(atm, v);
  return v;
}

export function range(begin, end) {
  let b = begin,
    e = end;
  if (e === undefined) {
    e = b;
    b = 0;
  }
  let ret = [];
  for (let x = b; x < e; x++) {
    ret.push(x);
  }
  return ret;
}

export function re_matches(re, s) {
  let matches = re.exec(s);
  if (matches && s === matches[0]) {
    if (matches.length === 1) {
      return matches[0];
    } else {
      return matches;
    }
  }
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

export const mapv = map;

export const vec = (x) => x;

export function apply(f, ...args) {
  const xs = args.slice(0, args.length - 1);
  const coll = args[args.length - 1];
  return f.apply(null, xs.concat(coll));
}

export function even_QMARK_(x) {
  return x % 2 == 0;
}

export function odd_QMARK_(x) {
  return !even_QMARK_(x);
}

export function complement(f) {
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
  return x instanceof Array;
}

export function concat(...colls) {
  var ret = [];
  for (const x of colls) {
    ret.push(...es6_iterator(x));
  }
  return ret;
}

export function mapcat(f, ...colls) {
  return concat(...map(f, ...colls));
}

export function identity(x) {
  return x;
}

export function interleave(...colls) {
  let ret = [];
  const iters = colls.map(es6_iterator);
  while (true) {
    let items = [];
    for (const i of iters) {
      const nextVal = i.next();
      if (nextVal.done) {
        return ret;
      }
      items.push(nextVal.value);
    }
    ret.push(...items);
  }
}

export function select_keys(o, ks) {
  const type = typeConst(o);
  // ret could be object or array, but in the future, maybe we'll have an IEmpty protocol
  const ret = emptyOfType(type);
  for (const k of ks) {
    const v = get(o, k);
    if (v != undefined) {
      assoc_BANG_(ret, k, v);
    }
  }
  return ret;
}

export function partition_all(n, ...args) {
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

function partitionInternal(n, step, pad, coll, all) {
  let ret = [];
  let array = [...es6_iterator(coll)];
  for (var i = 0; i < array.length; i = i + step) {
    let p = array.slice(i, i + n);
    if (p.length === n) {
      ret.push(p);
    } else if (pad.length) {
      p.push(...pad.slice(0, n - p.length));
      ret.push(p);
    } else if (all) {
      ret.push(p);
    }
  }
  return ret;
}

export function empty(coll) {
  const type = typeConst(coll);
  return emptyOfType(type);
}


export function merge(f, ...rest) {
  // if the first arg is nil we coerce it into a map.
  if (f === null || f === undefined)
    f = {};
  if (typeConst(f) === undefined)
    throw new Error(`${f} is not a Collection type.`);
  return conj_BANG_(f, ...rest);
}

export function system_time() {
  return performance.now();
}

export function into(...args) {
  switch (args.length) {
    case 0:
      return [];
    case 1:
      return args[0];
    default:
      return conj(args[0] ?? [], ...iterable(args[1]));
  }
}
