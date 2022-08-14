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

let object = Object.getPrototypeOf({});
let array = Object.getPrototypeOf([]);
let set = Object.getPrototypeOf(new Set());

export function conj(o, x) {
  switch (Object.getPrototypeOf(o)) {
    case object:
      let o2 = {...o};
      o2[x[0]] = x[1];
      return o2;
    case array:
      return [...o, x];
    case set:
      return new Set([...o, x]);
    default:
      return o.conj(x);
  }
}

export function dissoc_BANG_(m, k) {
  delete m[k];
  return m;
}

export function inc(n) {
    return n+1;
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
