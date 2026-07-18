// ADR 0007 POC - perf. Does making core fns protocol-free (so the slots DCE
// out) slow the hot path? Models `get` four ways and benchmarks the common
// case: read a key from a plain object.
//
// Each variant runs in its own process (spawned below) so it gets a clean,
// monomorphic JIT - a single shared call site would go megamorphic and the
// first variant measured would look artificially fast.
//
//   node perf.js              # run the interleaved benchmark
//   EXTENDED=1 node perf.js   # simulate an unrelated extend-type in the app
//   node perf.js <variant>    # (internal) benchmark one variant, print ns/op

const ILookup__lookup = Symbol('ILookup_-lookup');

function get_baseline(coll, key, nf) { // current squint: names the slot -> pins it
  if (coll == null) return nf;
  if (coll.constructor === Object) { const v = coll[key]; return v === undefined ? nf : v; }
  if (Array.isArray(coll)) { const v = coll[key]; return v === undefined ? nf : v; }
  if (coll instanceof Map) return coll.has(key) ? coll.get(key) : nf;
  if (coll[ILookup__lookup] !== undefined) return coll[ILookup__lookup](coll, key, nf);
  return nf;
}
function get_free(coll, key, nf) { // native only, no slot (not runtime-extensible)
  if (coll == null) return nf;
  if (coll.constructor === Object) { const v = coll[key]; return v === undefined ? nf : v; }
  if (Array.isArray(coll)) { const v = coll[key]; return v === undefined ? nf : v; }
  if (coll instanceof Map) return coll.has(key) ? coll.get(key) : nf;
  return nf;
}
const holder = { get: (c, k, nf) => get_free(c, k, nf) }; // extend-type wraps holder.get
function get_holder(coll, key, nf) { return holder.get(coll, key, nf); }
const registry = new Map(); // constructor -> impl ; base consults it only for non-native
function get_registry(coll, key, nf) {
  if (coll == null) return nf;
  if (coll.constructor === Object) { const v = coll[key]; return v === undefined ? nf : v; }
  if (Array.isArray(coll)) { const v = coll[key]; return v === undefined ? nf : v; }
  if (coll instanceof Map) return coll.has(key) ? coll.get(key) : nf;
  if (registry.size) { const f = registry.get(coll.constructor); if (f) return f(coll, key, nf); }
  return nf;
}
if (process.env.EXTENDED) { // an unrelated protocol extension exists somewhere in the app
  class Other {}
  const prev = holder.get;
  holder.get = (c, k, nf) => (c instanceof Other ? nf : prev(c, k, nf));
  registry.set(Other, (c, k, nf) => nf);
}

const objs = Array.from({ length: 1024 }, (_, i) => ({ id: i, label: 'row ' + i, selected: false }));

function benchOne(V) {
  const loop = (iters) => {
    let s; const t0 = process.hrtime.bigint();
    switch (V) {
      case 'baseline': for (let i = 0; i < iters; i++) s = get_baseline(objs[i & 1023], 'label', null); break;
      case 'free':     for (let i = 0; i < iters; i++) s = get_free(objs[i & 1023], 'label', null); break;
      case 'holder':   for (let i = 0; i < iters; i++) s = get_holder(objs[i & 1023], 'label', null); break;
      case 'registry': for (let i = 0; i < iters; i++) s = get_registry(objs[i & 1023], 'label', null); break;
    }
    globalThis.__sink = s;
    return Number(process.hrtime.bigint() - t0) / iters;
  };
  loop(500000);
  let best = Infinity;
  for (let r = 0; r < 5; r++) best = Math.min(best, loop(3000000));
  return best;
}

const NAMES = { baseline: 'baseline (inline slot)', free: 'protocol-free direct', holder: 'holder / wrapping', registry: 'registry guard' };

if (process.argv[2]) { console.log(benchOne(process.argv[2]).toFixed(4)); process.exit(0); }

const { execFileSync } = require('node:child_process');
const ROUNDS = 9, samples = { baseline: [], free: [], holder: [], registry: [] };
for (let r = 0; r < ROUNDS; r++)
  for (const n of Object.keys(samples))
    samples[n].push(parseFloat(execFileSync(process.execPath, [__filename, n], { encoding: 'utf8', env: process.env })));
const median = (a) => [...a].sort((x, y) => x - y)[a.length >> 1];
const base = median(samples.baseline);
console.log(`get "label" from a plain object, median of ${ROUNDS} procs${process.env.EXTENDED ? ' (unrelated extend-type present)' : ''}:`);
for (const n of Object.keys(samples)) {
  const m = median(samples[n]);
  console.log('  ' + NAMES[n].padEnd(24), m.toFixed(4), 'ns  ' + (m / base).toFixed(3) + 'x');
}
