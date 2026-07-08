import * as c from '../../src/squint/core.js';
import * as h from '../../src/squint/immutable.js';

let fails = 0;
function is(actual, expected, msg) {
  if (!c._EQ_(actual, expected)) {
    fails++;
    console.log('FAIL', msg, '| actual:', c.pr_str(actual), '| expected:', c.pr_str(expected));
  }
}

// basics
const v = h.vector(1, 2, 3);
is(h.vector_QMARK_(v), true, 'vector? (module)');
is(c.vector_QMARK_(v), true, 'core vector?');
is(c.sequential_QMARK_(v), true, 'sequential?');
is(c.count(v), 3, 'count');
is(c.nth(v, 0), 1, 'nth 0');
is(c.nth(v, 2), 3, 'nth 2');
is(c.nth(v, 9, 'dflt'), 'dflt', 'nth default');
is(c.get(v, 1), 2, 'get by index');
is(c.get(v, 'x'), undefined, 'get non-number');
is(c.get(v, 9, 42), 42, 'get default');
let threw = false;
try { c.nth(v, 9); } catch (e) { threw = true; }
is(threw, true, 'nth oob throws');

// conj/assoc persistence
const v2 = c.conj(v, 4);
is(c.count(v), 3, 'original untouched');
is(c.count(v2), 4, 'conj grows');
is(c.nth(v2, 3), 4, 'conj value');
const v3 = c.assoc(v, 1, 'x');
is(c.nth(v3, 1), 'x', 'assoc replaces');
is(c.nth(v, 1), 2, 'assoc persistent');
is(c.count(c.assoc(v, 3, 'end')), 4, 'assoc at cnt appends');
threw = false;
try { c.assoc(v, 5, 'oob'); } catch (e) { threw = true; }
is(threw, true, 'assoc oob throws');
is(c.contains_QMARK_(v, 2), true, 'contains? index');
is(c.contains_QMARK_(v, 3), false, 'contains? oob');

// peek/pop/subvec
is(c.peek(v), 3, 'peek');
is(c.count(c.pop(v)), 2, 'pop');
is(c.peek(c.pop(v)), 2, 'peek after pop');
is(c.vec(c.subvec(v, 1)), [2, 3], 'subvec');
is(c.vec(c.subvec(v, 1, 2)), [2], 'subvec range');
is(h.vector_QMARK_(c.subvec(v, 1)), true, 'subvec keeps type');

// equality both ways, hash
is(c._EQ_(v, [1, 2, 3]), true, 'pvec = array');
is(c._EQ_([1, 2, 3], v), true, 'array = pvec');
is(c._EQ_(v, h.vector(1, 2, 3)), true, 'pvec = pvec');
is(c._EQ_(v, c.list(1, 2, 3)), true, 'pvec = list');
is(c._EQ_(v, c.map(c.inc, [0, 1, 2])), true, 'pvec = lazy seq');
is(c._EQ_(v, [1, 2]), false, 'length mismatch');
is(c._EQ_(v, new Set([1, 2, 3])), false, 'pvec != set');
is(h.hash(v) === h.hash([1, 2, 3]), true, 'hash = array hash');

// pvec as map key, hit by array
const m = c.assoc(h.hash_map(), h.vector(1, 2), 'v');
is(c.get(m, [1, 2]), 'v', 'pvec key hit by array');
// pvec as conj entry on hamt map
is(c.get(c.conj(h.hash_map(), h.vector('k', 9)), 'k'), 9, 'pvec entry conj on hamt map');

// seq ops / destructuring / reduce
is(c.vec(c.map((x) => x + 1, v)), [2, 3, 4], 'map over pvec');
is(c.reduce((a, b) => a + b, 0, v), 6, 'reduce');
is(c.reduce_kv((acc, i, x) => acc + i + x, 0, v), 9, 'reduce-kv');
is(c.reduce_kv((acc, i, x) => c.reduced('early'), 0, v), 'early', 'reduce-kv reduced');
is(c.first(v), 1, 'first');
is(c.vec(c.rest(v)), [2, 3], 'rest');
is(c.last(v), 3, 'last');
is(c.into([], v), [1, 2, 3], 'into array from pvec');
is(h.vector_QMARK_(c.into(h.vector(), [1, 2])), true, 'into pvec keeps type');
is(c.count(c.into(h.vector(1), [2, 3])), 3, 'into pvec');

// empty / vec / print / clj->js
is(c.count(c.empty(v)), 0, 'empty');
is(h.vector_QMARK_(c.empty(v)), true, 'empty keeps type');
is(h.vec([1, 2]) instanceof Object, true, 'vec from array');
is(c.count(h.vec(new Set([1, 2, 3]))), 3, 'vec from set');
is(c.pr_str(v), '[1 2 3]', 'pr-str');
is(c.pr_str(h.vector(h.vector(1), h.hash_map('a', 1))), '[[1] {:a 1}]', 'pr-str nested');
const js = c.clj__GT_js(h.vector(1, h.vector(2)));
is(Array.isArray(js), true, 'clj->js array');
is(Array.isArray(js[1]), true, 'clj->js deep');

// transient
const t = c.transient$(h.vector(1));
c.conj_BANG_(t, 2);
c.assoc_BANG_(t, 0, 'x');
c.pop_BANG_(t);
const pt = c.persistent_BANG_(t);
is(c.count(pt), 1, 'transient ops');
is(c.nth(pt, 0), 'x', 'transient assoc!');
threw = false;
try { c.conj_BANG_(t, 3); } catch (e) { threw = true; }
is(threw, true, 'transient invalidated');

// scale: tail -> tree -> deeper levels and back
let big = h.vector();
const N = 3000;
for (let i = 0; i < N; i++) big = c.conj(big, i);
is(c.count(big), N, 'big count');
let ok = true;
for (let i = 0; i < N; i++) if (c.nth(big, i) !== i) { ok = false; break; }
is(ok, true, 'big nth all correct');
is(c.nth(c.assoc(big, 1500, 'mid'), 1500), 'mid', 'assoc deep');
let shrunk = big;
for (let i = 0; i < N; i++) shrunk = c.pop(shrunk);
is(c.count(shrunk), 0, 'pop all the way down');
is(c._EQ_(shrunk, h.vector()), true, 'empty after teardown');
let sum = 0;
for (const x of big) sum += x;
is(sum, (N * (N - 1)) / 2, 'iterator sums');

console.log(fails === 0 ? 'ALL PASS' : fails + ' FAILURES');
process.exit(fails === 0 ? 0 : 1);
