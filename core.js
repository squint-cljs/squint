export function assoc_BANG_(m, k, v, ...kvs) {
  m[k] = v;
  if (kvs.length != 0) {
    return assoc_BANG_(m, ...kvs);
  }
  else return m;
}

export function assoc(o, k, v, ...kvs) {
  let o2 = { ...o };
  return assoc_BANG_(o2, k, v, ...kvs);
}

const object = Object.getPrototypeOf({});
const array = Object.getPrototypeOf([]);
const set = Object.getPrototypeOf(new Set());

export function conj_BANG_(o, x, ...xs) {
  switch (Object.getPrototypeOf(o)) {
  case object:
    o[x[0]] = x[1];
    break;
  case array:
    o.push(x);
    break;
  case set:
    o.add(x);
    break;
  default:
    o.conj_BANG_(x);
  }
  if (xs.length != 0) {
    return conj_BANG_(o, ...xs);
  }
  return o;
}

export function conj(o, x, ...xs) {
  switch (Object.getPrototypeOf(o)) {
  case object:
    let o2 = {...o};
    return conj_BANG_(o2, x, ...xs);
  case array:
    return [...o, x, ...xs];
  case set:
    return new Set([...o, x, ...xs]);
  default:
    return o.conj(x, ...xs);
  }
}

export function disj_BANG_(s, ...xs) {
  for (let x of xs) {
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
  return n+1;
}

export function dec(n) {
  return n-1;
}

export function println(...args) {
  console.log(...args);
}

export function nth(coll, idx) {
  return coll[idx];
}

export function map(f, coll) {
  return coll.map(f);
}

export function str(...xs) {
  let ret = "";
  xs.forEach(x => ret = ret + x);
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
  return JSON.stringify(
    x,
    (_key, value) => (value instanceof Set ? [...value] : value)
  );
}

export function pr_str(...xs) {
  return xs.map(pr_str_1).join(" ");
}

export function prn(...xs) {
  println(pr_str(...xs));
}

export function Atom(init) {
  this.val = init;
  this._deref = () => this.val;
  this._reset_BANG_ = (x) => this.val = x;
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
  let b = begin, e = end;
  if (e === undefined) {
    e = b; b = 0;
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
    let res = f(ctr,x);
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
