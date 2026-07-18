// ADR 0007 POC - extensibility. In the registry model (base fn does native
// dispatch inline, then consults a registry for non-native types), can a user
// still extend a built-in protocol? Shows: yes for their own types; no for
// native types - which matches current squint, where natives are fast-pathed
// inline before any slot check.
//
//   node extend.js

const reg = new Map(); // protocol -> (constructor -> impl)
function extendType(proto, Type, fn) {
  if (!reg.has(proto)) reg.set(proto, new Map());
  reg.get(proto).set(Type, fn);
}
function get(coll, key, nf) {
  if (coll == null) return nf;
  if (coll.constructor === Object) { const v = coll[key]; return v === undefined ? nf : v; } // native inline
  if (Array.isArray(coll))         { const v = coll[key]; return v === undefined ? nf : v; } // native inline
  if (reg.size) { const m = reg.get('ILookup'); const f = m && m.get(coll.constructor); if (f) return f(coll, key, nf); }
  return nf;
}

class RangeMap { constructor(lo, hi) { this.lo = lo; this.hi = hi; } }
extendType('ILookup', RangeMap, (rm, k, nf) => (k >= rm.lo && k <= rm.hi ? k * 10 : nf)); // own type -> built-in protocol
extendType('ILookup', Array, () => 'CUSTOM-ARRAY');                                         // native type -> built-in protocol

const ok = (a, b, m) => console.log((a === b ? 'ok  ' : 'FAIL') + '  ' + m + '  => ' + a);
ok(get(new RangeMap(1, 5), 3, 'nf'), 30, 'own type extends ILookup, hit');
ok(get(new RangeMap(1, 5), 9, 'nf'), 'nf', 'own type extends ILookup, miss');
ok(get([10, 20, 30], 1, 'nf'), 20, 'native Array: inline wins, extension ignored (as in current squint)');
ok(get({ x: 1 }, 'x', 'nf'), 1, 'plain object: native inline, not overridable');
