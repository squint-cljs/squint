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

export function assoc_in(o, keys, value) {
  if (!(o instanceof Object)) {
    throw new Error(
      'Illegal argument: assoc-in expects the first argument to be a Map, Array, or Object.'
    );
  }

  if (!(keys instanceof Array)) {
    throw new Error('Illegal argument: assoc-in expects the keys argument to be an Array.');
  }

  const chain = [o];
  let lastInChain = o;

  for (let i = 0; i < keys.length - 1; i += 1) {
    const chainValue = lastInChain instanceof Map ? lastInChain.get(keys[i]) : lastInChain[keys[i]];
    if (!(chainValue instanceof Object)) {
      throw new Error(
        'Illegal argument: assoc-in expects each intermediate value found via the keys array to be a Map, Array, or Object.'
      );
    }
    chain.push(chainValue);
    lastInChain = chainValue;
  }

  chain.push(value);

  for (let i = chain.length - 2; i >= 0; i -= 1) {
    chain[i] = assoc(chain[i], keys[i], chain[i + 1]);
  }

  return chain[0];
}

export function assoc_in_BANG_(o, keys, value) {
  if (!(o instanceof Object)) {
    throw new Error(
      'Illegal argument: assoc-in expects the first argument to be a Map, Array, or Object.'
    );
  }

  if (!(keys instanceof Array)) {
    throw new Error('Illegal argument: assoc-in expects the keys argument to be an Array.');
  }

  const chain = [o];
  let lastInChain = o;

  for (let i = 0; i < keys.length - 1; i += 1) {
    const chainValue = lastInChain instanceof Map ? lastInChain.get(keys[i]) : lastInChain[keys[i]];
    if (!(chainValue instanceof Object)) {
      throw new Error(
        'Illegal argument: assoc-in expects each intermediate value found via the keys array to be a Map, Array, or Object.'
      );
    }
    chain.push(chainValue);
    lastInChain = chainValue;
  }

  chain.push(value);

  for (let i = chain.length - 2; i >= 0; i -= 1) {
    assoc_BANG_(chain[i], keys[i], chain[i + 1]);
  }

  return chain[0];
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
  if (coll instanceof Map) {
    return coll.has(key) ? coll.get(key) : otherwise;
  }

  return key in coll ? coll[key] : otherwise;
}

export function map(f, coll) {
  return coll.map(f);
}

export function str(...xs) {
  let ret = '';
  xs.forEach((x) => (ret = ret + x));
  return ret;
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

export function map_indexed(f, coll) {
  let ctr = 0;
  let f2 = (x) => {
    let res = f(ctr, x);
    ctr = ctr + 1;
    return res;
  };
  return coll.map(f2);
}

export const mapv = map;

export const vec = (x) => x;

export function apply(f, ...args) {
  return f.apply(null, ...args);
}
