export function assoc(m, k, v, ...kvs) {
  m[k] = v;
  if (kvs.length != 0) {
    return assoc(m, ...kvs);
  }
  else return m;
}

export function dissoc(m, k) {
  delete m[k];
  return m;
}

export function keyword(s) {
  return s;
}

