import { iterable } from './core.js';

export function blank_QMARK_(s) {
  if (!s) return true;
  if (s.length === 0) return true;
  if (s.trimLeft().length === 0) return true;
  return false;
}

export function join(sep, coll) {
  if (coll === undefined) {
    coll = sep;
    sep = '';
  }
  if (coll instanceof Array) {
    return coll.join(sep);
  }
  let ret = '';
  let addSep = false;
  for (const o of iterable(coll)) {
    if (addSep) ret += sep;
    ret += o;
    addSep = true;
  }
  return ret;
}

export function trim(s) {
  return s.trim();
}

export function split(s, re) {
  return s.split(re);
}

export function starts_with_QMARK_(s, substr) {
  return s.startsWith(substr);
}
