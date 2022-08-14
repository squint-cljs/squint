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
      console.log("obj")
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
