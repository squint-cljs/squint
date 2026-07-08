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
const s = h.hash_set(1, 2, 3);
is(h.hash_set_QMARK_(s), true, 'hash-set?');
is(c.set_QMARK_(s), true, 'core set?');
is(c.count(s), 3, 'count');
is(c.contains_QMARK_(s, 2), true, 'contains?');
is(c.contains_QMARK_(s, 9), false, 'not contains?');
is(c.get(s, 2), 2, 'get returns element');
is(c.get(s, 9, 'nf'), 'nf', 'get default');
is(c.count(h.hash_set(1, 1, 2)), 2, 'dedupes');

// conj/disj persistence
const s2 = c.conj(s, 4);
is(c.count(s), 3, 'original untouched');
is(c.count(s2), 4, 'conj grows');
is(c.count(c.conj(s, 2)), 3, 'conj existing no-op');
const s3 = c.disj(s, 1);
is(c.count(s3), 2, 'disj shrinks');
is(c.contains_QMARK_(s3, 1), false, 'disj removes');
is(c.count(c.disj(s, 99)), 3, 'disj absent no-op');

// value semantics: composite elements
const cs = h.hash_set([1, 2], { a: 1 });
is(c.contains_QMARK_(cs, [1, 2]), true, 'vector element by value');
is(c.contains_QMARK_(cs, { a: 1 }), true, 'object element by value');
is(c.contains_QMARK_(cs, c.list(1, 2)), true, 'list hits vector element');
is(c.count(c.conj(cs, [1, 2])), 2, 'value-equal conj dedupes');
is(c.contains_QMARK_(c.conj(h.hash_set(), h.vector(1, 2)), [1, 2]), true, 'pvec element hit by array');

// equality both ways, vs js/Set and sorted-set
is(c._EQ_(h.hash_set(1, 2), h.hash_set(2, 1)), true, 'set = set order-free');
is(c._EQ_(h.hash_set(1, 2), new Set([1, 2])), true, 'pset = js Set');
is(c._EQ_(new Set([1, 2]), h.hash_set(1, 2)), true, 'js Set = pset');
is(c._EQ_(h.hash_set(1, 2), c.sorted_set(2, 1)), true, 'pset = sorted-set');
is(c._EQ_(h.hash_set(1, 2), new Set([1])), false, 'size mismatch');
is(c._EQ_(h.hash_set(1, 2), [1, 2]), false, 'pset != vector');
is(h.hash(h.hash_set(1, 2)) === h.hash(new Set([2, 1])), true, 'hash = js Set hash');

// set as map key / map as set element
is(c.get(c.assoc(h.hash_map(), h.hash_set(1, 2), 'v'), new Set([2, 1])), 'v', 'pset key hit by js Set');
is(c.contains_QMARK_(h.hash_set(h.hash_map('a', 1)), { a: 1 }), true, 'map element by value');

// seq ops
is(new Set(c.vec(c.map((x) => x + 1, s))), new Set([2, 3, 4]), 'map over pset');
is(c.reduce((a, b) => a + b, 0, s), 6, 'reduce');
is(c.count(c.into(h.hash_set(), [1, 2, 2, 3])), 3, 'into pset dedupes');
is(h.hash_set_QMARK_(c.into(h.hash_set(), [1])), true, 'into keeps type');
is(c.count(c.filter((x) => x > 1, s)), 2, 'filter');
is(h.hash_set_QMARK_(c.empty(s)), true, 'empty keeps type');
is(c.count(c.empty(s)), 0, 'empty count');

// conversion + print + clj->js
is(c.count(h.set([1, 2, 2])), 2, 'set from array dedupes');
is(c.count(h.set(new Set([1, 2]))), 2, 'set from js Set');
is(h.set(s) === s, true, 'set of pset identity');
is(c.pr_str(h.hash_set('a')), '#{"a"}', 'pr-str');
const js = c.clj__GT_js(h.hash_set(1));
is(Array.isArray(js), true, 'clj->js array');

// transient
const t = c.transient$(h.hash_set(1));
c.conj_BANG_(t, 2);
c.disj_BANG_(t, 1);
const pt = c.persistent_BANG_(t);
is(h.hash_set_QMARK_(pt), true, 'persistent! type');
is(c._EQ_(pt, h.hash_set(2)), true, 'transient ops');
let threw = false;
try { c.conj_BANG_(t, 3); } catch (e) { threw = true; }
is(threw, true, 'transient invalidated');

// scale
let big = h.hash_set();
const N = 5000;
for (let i = 0; i < N; i++) big = c.conj(big, 'e' + i);
is(c.count(big), N, 'big count');
let ok = true;
for (let i = 0; i < N; i++) if (!c.contains_QMARK_(big, 'e' + i)) { ok = false; break; }
is(ok, true, 'big membership');
let shrunk = big;
for (let i = 0; i < N; i++) shrunk = c.disj(shrunk, 'e' + i);
is(c.count(shrunk), 0, 'disj all');
is(c._EQ_(shrunk, h.hash_set()), true, 'empty after teardown');

console.log(fails === 0 ? 'ALL PASS' : fails + ' FAILURES');
process.exit(fails === 0 ? 0 : 1);
