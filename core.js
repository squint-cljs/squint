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

  if (m instanceof Map) {
    m.set(k, v);

    for (let i = 0; i < kvs.length; i += 2) {
      m.set(kvs[i], kvs[i + 1]);
    }
  } else if (m instanceof Object) {
    m[k] = v;

    for (let i = 0; i < kvs.length; i += 2) {
      m[kvs[i]] = kvs[i + 1];
    }
  } else {
    throw new Error(
      'Illegal argument: assoc! expects a Map, Array, or Object as the first argument.'
    );
  }

  return m;
}

export function assoc(o, k, v, ...kvs) {
  if (!(o instanceof Object)) {
    throw new Error(
      'Illegal argument: assoc expects a Map, Array, or Object as the first argument.'
    );
  }

  if (o instanceof Map) {
    return assoc_BANG_(new Map(o.entries()), k, v, ...kvs);
  } else if (o instanceof Array) {
    return assoc_BANG_([...o], k, v, ...kvs);
  }

  return assoc_BANG_({ ...o }, k, v, ...kvs);
}

const MAP_TYPE = 1;
const ARRAY_TYPE = 2;
const OBJECT_TYPE = 3;

function newEmptyOfType(type) {
  switch (type) {
    case MAP_TYPE:
      return new Map();
      break;
    case ARRAY_TYPE:
      return [];
      break;
    case OBJECT_TYPE:
      return {};
      break;
  }
  return undefined;
}

function typeConst(obj) {
  if (obj instanceof Map) return MAP_TYPE;
  if (obj instanceof Array) return ARRAY_TYPE;
  if (obj instanceof Object) return OBJECT_TYPE;
  return undefined;
}

function assoc_in_with(f, fname, o, keys, value) {
  let baseType = typeConst(o);
  if (!baseType)
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
      chainValue = newEmptyOfType(baseType);
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
  let [o, ...rest] = xs;

  if (o === null || o === undefined) {
    o = [];
  }

  if (o instanceof Set) {
    for (const x of rest) {
      o.add(x);
    }
  } else if (o instanceof Array) {
    o.push(...rest);
  } else if (o instanceof Map) {
    for (const x of rest) {
      o.set(x[0], x[1]);
    }
  } else if (o instanceof Object) {
    for (const x of rest) {
      o[x[0]] = x[1];
    }
  } else {
    throw new Error(
      'Illegal argument: conj! expects a Set, Array, Map, or Object as the first argument.'
    );
  }

  return o;
}

export function conj(...xs) {
  let [o, ...rest] = xs;

  if (o === null || o === undefined) {
    o = [];
  }

  if (o instanceof Set) {
    return new Set([...o, ...rest]);
  } else if (o instanceof Array) {
    return [...o, ...rest];
  } else if (o instanceof Map) {
    const m = new Map(o);

    for (const x of rest) {
      m.set(x[0], x[1]);
    }

    return m;
  } else if (o instanceof Object) {
    const o2 = { ...o };

    for (const x of rest) {
      o2[x[0]] = x[1];
    }

    return o2;
  }

  throw new Error(
    'Illegal argument: conj expects a Set, Array, Map, or Object as the first argument.'
  );
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
  if (coll === null || typeof coll !== "object") return otherwise;
  if (coll instanceof Map) {
    return coll.has(key) ? coll.get(key) : otherwise;
  }

  return key in coll ? coll[key] : otherwise;
}

export function seqable_QMARK_(x) {
  // String is iterable but doesn't allow `m in s`
  return typeof x === 'string' || x === null || x === undefined || Symbol.iterator in x;
}

export function iterable(x) {
  // nil puns to empty iterable, support passing nil to first/rest/reduce, etc.
  if (x === null || x === undefined) {
    return [];
  }
  if (seqable_QMARK_(x)) {
    return x;
  }
  return Object.entries(x);
}

export function seq(x) {
  let iter = iterable(x);
  // return nil for terminal checking
  if (iter.length === 0 || iter.size === 0) {
    return null;
  }
  return iter;
}

export function first(coll) {
  // destructuring uses iterable protocol
  let [first] = iterable(coll);
  return first;
}

export function second(coll) {
  let [_, v] = iterable(coll);
  return v;
}

export function rest(coll) {
  let [_, ...rest] = iterable(coll);
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
    const [hd, ...more] = iterable(arg1);
    val = hd;
    coll = more;
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

export function map(f, coll) {
  let ret = [];
  for (const x of iterable(coll)) {
    ret.push(f(x));
  }
  return ret;
}

export function map_indexed(f, coll) {
  let ret = [];
  let i = 0;
  for (const x of iterable(coll)) {
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

export const mapv = map;

export const vec = (x) => x;

export function apply(f, ...args) {
  return f.apply(null, ...args);
}

export function even_QMARK_(x) {
  return x % 2 == 0;
}

export function odd_QMARK_(x) {
  return !even_QMARK_(x);
}

export function constantly(x) {
  return (..._) => x;
}
