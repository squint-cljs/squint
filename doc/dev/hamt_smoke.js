import * as c from '../../src/squint/core.js';
import * as h from '../../src/squint/hamt.js';

let fails = 0;
function is(actual, expected, msg) {
  const ok = c._EQ_(actual, expected);
  if (!ok) {
    fails++;
    console.log('FAIL', msg, '| actual:', c.pr_str(actual), '| expected:', c.pr_str(expected));
  }
}

// basics
const m = h.hash_map('a', 1, 'b', 2);
is(c.get(m, 'a'), 1, 'get');
is(c.get(m, 'zz', 42), 42, 'get default');
is(c.count(m), 2, 'count');
const m2 = c.assoc(m, 'c', 3);
is(c.count(m), 2, 'persistence: original untouched');
is(c.count(m2), 3, 'assoc grows');
is(c.get(m2, 'c'), 3, 'assoc value');
const m3 = c.dissoc(m2, 'a');
is(c.count(m3), 2, 'dissoc shrinks');
is(c.get(m3, 'a'), undefined, 'dissoc removes');
is(c.contains_QMARK_(m2, 'c'), true, 'contains?');
is(c.contains_QMARK_(m2, 'zz'), false, 'not contains?');
is(h.hash_map_QMARK_(m), true, 'hash-map?');
is(c.map_QMARK_(m), true, 'map?');
is(c.associative_QMARK_(m), true, 'associative?');

// nil key, false key, undefined-as-nil
const mn = c.assoc(m, null, 'nilval', false, 'falseval', 0, 'zeroval');
is(c.get(mn, null), 'nilval', 'nil key');
is(c.get(mn, undefined), 'nilval', 'undefined key puns to nil');
is(c.get(mn, false), 'falseval', 'false key');
is(c.get(mn, 0), 'zeroval', 'zero key distinct from false');
is(c.count(mn), 5, 'count with nil/false/zero keys');
is(c.count(c.dissoc(mn, null)), 4, 'dissoc nil key');

// composite (value-equal) keys: the whole point
const mv = c.assoc(h.hash_map(), [1, 2], 'vec', { x: 1 }, 'obj');
is(c.get(mv, [1, 2]), 'vec', 'vector key by value');
is(c.get(mv, c.list(1, 2)), 'vec', 'list key equals vector key');
is(c.get(mv, c.map(c.inc, [0, 1])), 'vec', 'lazy seq key equals vector key');
is(c.get(mv, { x: 1 }), 'obj', 'object key by value');
is(c.get(mv, [2, 1]), undefined, 'order matters');
is(c.get(mv, ['1', 2]), undefined, 'no string/number key collision');

// known Java-hash collision pair: "Aa" and "BB"
const mc = h.hash_map('Aa', 1, 'BB', 2);
is(c.count(mc), 2, 'collision: both stored');
is(c.get(mc, 'Aa'), 1, 'collision: first found');
is(c.get(mc, 'BB'), 2, 'collision: second found');
is(c.count(c.dissoc(mc, 'Aa')), 1, 'collision: dissoc one');
is(c.get(c.dissoc(mc, 'Aa'), 'BB'), 2, 'collision: other survives');

// conj: entry, map arg merges
is(c.get(c.conj(m, ['k', 9]), 'k'), 9, 'conj entry vector');
is(c.get(c.conj(m, { d: 4 }), 'd'), 4, 'conj plain-object merges');
is(c.count(c.conj(m, h.hash_map('e', 5))), 3, 'conj hamt map merges');

// into / merge
const mi = c.into(h.hash_map(), [['a', 1], ['b', 2]]);
is(c.count(mi), 2, 'into from entry vectors');
is(h.hash_map_QMARK_(mi), true, 'into preserves type');
const mm = c.merge(h.hash_map('a', 1), { b: 2 }, new Map([['c', 3]]));
is(h.hash_map_QMARK_(mm), true, 'merge keeps hamt type');
is(c.get(mm, 'b'), 2, 'merge from object');
is(c.get(mm, 'c'), 3, 'merge from js Map');

// equality, both directions, and against plain object / js Map
is(c._EQ_(h.hash_map('a', 1), h.hash_map('a', 1)), true, 'hamt = hamt');
is(c._EQ_(h.hash_map('a', 1), { a: 1 }), true, 'hamt = object');
is(c._EQ_({ a: 1 }, h.hash_map('a', 1)), true, 'object = hamt (right dispatch)');
is(c._EQ_(h.hash_map('a', 1), new Map([['a', 1]])), true, 'hamt = js Map');
is(c._EQ_(h.hash_map('a', 1), { a: 2 }), false, 'value mismatch');
is(c._EQ_(h.hash_map('a', 1, 'b', 2), { a: 1 }), false, 'count mismatch');
is(c._EQ_(h.hash_map(), {}), true, 'empty = empty object');

// hash consistency: = implies same hash
is(h.hash([1, 2, 3]) === h.hash(c.list(1, 2, 3)), true, 'vector/list hash agree');
is(h.hash([1, 2, 3]) === h.hash(c.map(c.inc, [0, 1, 2])), true, 'lazy seq hash agrees');
is(h.hash({ a: 1, b: 2 }) === h.hash(new Map([['b', 2], ['a', 1]])), true, 'obj/Map hash agree, order-free');
is(h.hash(new Set([1, 2])) === h.hash(new Set([2, 1])), true, 'set hash order-free');
is(h.hash(h.hash_map('a', 1)) === h.hash({ a: 1 }), true, 'hamt hash = object hash');
is(h.hash('Aa') === h.hash('BB'), true, 'known collision pair collides');
is(h.hash(c.sorted_map('a', 1)) === h.hash({ a: 1 }), true, 'sorted-map hash = object hash');
is(h.hash(c.sorted_set(1, 2)) === h.hash(new Set([2, 1])), true, 'sorted-set hash = Set hash');

// maps as keys of maps (needs hash + =)
const mk = c.assoc(h.hash_map(), h.hash_map('a', 1), 'mapkey');
is(c.get(mk, { a: 1 }), 'mapkey', 'plain-object key hits hamt-map key');

// seq / first / reduce-kv / keys / vals
const sm = h.hash_map('a', 1);
is(c.seq(h.hash_map()), null, 'seq of empty is nil');
is(c.seq(sm), [['a', 1]], 'seq yields entries');
is(c.map_entry_QMARK_(c.first(c.seq(sm))), true, 'seq entries are map entries');
is(c.reduce_kv((acc, k, v) => acc + v, 0, h.hash_map('a', 1, 'b', 2)), 3, 'reduce-kv');
is(new Set(c.keys(h.hash_map('a', 1, 'b', 2))), new Set(['a', 'b']), 'keys');
is(new Set(c.vals(h.hash_map('a', 1, 'b', 2))), new Set([1, 2]), 'vals');
is(c.reduce_kv((acc, k, v) => c.reduced('early'), 0, h.hash_map('a', 1, 'b', 2)), 'early', 'reduce-kv reduced');

// empty, get via fn position (ILookup)
is(c.count(c.empty(m2)), 0, 'empty');
is(h.hash_map_QMARK_(c.empty(m2)), true, 'empty keeps type');

// satisfies?
is(c.satisfies_QMARK_(c.IMap, m), true, 'satisfies? IMap');
is(c.satisfies_QMARK_(h.IHash, m), true, 'satisfies? IHash');

// transient round-trip
const t = c.transient$(h.hash_map());
c.assoc_BANG_(t, 'a', 1);
c.conj_BANG_(t, ['b', 2]);
const pt = c.persistent_BANG_(t);
is(c.count(pt), 2, 'transient round-trip count');
is(h.hash_map_QMARK_(pt), true, 'persistent! returns hamt');
let threw = false;
try { c.assoc_BANG_(t, 'c', 3); } catch (e) { threw = true; }
is(threw, true, 'transient invalidated after persistent!');

// printing
is(c.pr_str(h.hash_map('a', 1)), '{:a 1}', 'pr-str looks like a map');
is(c.pr_str(h.hash_map([1, 2], 'v')), '{[1 2] "v"}', 'pr-str composite key');

// scale: push into ArrayNode territory and back
let big = h.hash_map();
const N = 50000;
for (let i = 0; i < N; i++) big = c.assoc(big, 'k' + i, i);
is(c.count(big), N, 'big count');
let ok = true;
for (let i = 0; i < N; i++) if (c.get(big, 'k' + i) !== i) { ok = false; break; }
is(ok, true, 'big lookups all hit');
let shrunk = big;
for (let i = 0; i < N; i++) shrunk = c.dissoc(shrunk, 'k' + i);
is(c.count(shrunk), 0, 'dissoc all the way down');
is(c._EQ_(shrunk, h.hash_map()), true, 'empty after teardown');
// iterate whole big map
let sum = 0, cnt = 0;
for (const [k, v] of c.iterable(big)) { sum += v; cnt++; }
is(cnt, N, 'iterator covers all entries');
is(sum, (N * (N - 1)) / 2, 'iterator values sum');

console.log(fails === 0 ? 'ALL PASS' : fails + ' FAILURES');
process.exit(fails === 0 ? 0 : 1);
