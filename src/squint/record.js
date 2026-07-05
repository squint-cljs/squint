import {
  IRecord,
  ILookup__lookup,
  IAssociative__assoc,
  IAssociative__contains_key_QMARK_,
  IMap__dissoc,
  ICounted__count,
  IKVReduce__kv_reduce,
  ICollection__conj,
  IEquiv__equiv,
  ISeqable__seq,
  ISeqable,
  _EQ_,
  vector_QMARK_,
  seq,
} from './core.js';

// shared defrecord implementations: every record type points its protocol
// slots at these, so call sites stay monomorphic across record types
function recordLookup(rec, k, nf) {
  const v = rec[k];
  return v === undefined ? nf : v;
}
function recordCopy(rec) {
  return Object.assign(Object.create(Object.getPrototypeOf(rec)), rec);
}
function recordAssoc(rec, k, v) {
  const r = recordCopy(rec);
  r[k] = v;
  return r;
}
function recordContains(rec, k) {
  return Object.prototype.hasOwnProperty.call(rec, k);
}
function recordDissoc(rec, k) {
  // removing a basis field demotes to a plain map, like CLJS
  if (rec[IRecord.__sym].includes(k)) {
    const m = { ...rec };
    delete m[k];
    return m;
  }
  const r = recordCopy(rec);
  delete r[k];
  return r;
}
function recordCount(rec) {
  return Object.keys(rec).length;
}
function recordKvReduce(rec, f, init) {
  let acc = init;
  for (const k of Object.keys(rec)) acc = f(acc, k, rec[k]);
  return acc;
}
function recordConj(rec, x) {
  if (vector_QMARK_(x)) return recordAssoc(rec, x[0], x[1]);
  let r = rec;
  for (const e of seq(x) ?? []) r = recordAssoc(r, e[0], e[1]);
  return r;
}
function recordEquiv(rec, other) {
  if (other == null || Object.getPrototypeOf(rec) !== Object.getPrototypeOf(other)) return false;
  const ka = Object.keys(rec);
  if (ka.length !== Object.keys(other).length) return false;
  for (const k of ka) {
    if (!Object.prototype.hasOwnProperty.call(other, k) || !_EQ_(rec[k], other[k])) return false;
  }
  return true;
}
function recordSeq(rec) {
  return seq(Object.entries(rec));
}

export function attach(proto, basis) {
  proto[IRecord.__sym] = basis;
  proto[ILookup__lookup] = recordLookup;
  proto[IAssociative__assoc] = recordAssoc;
  proto[IAssociative__contains_key_QMARK_] = recordContains;
  proto[IMap__dissoc] = recordDissoc;
  proto[ICounted__count] = recordCount;
  proto[IKVReduce__kv_reduce] = recordKvReduce;
  proto[ICollection__conj] = recordConj;
  proto[IEquiv__equiv] = recordEquiv;
  proto[ISeqable__seq] = recordSeq;
  proto[ISeqable.__sym] = true;
  return null;
}
