// ADR 0007 POC - DCE. Does a protocol-free base actually let the slot symbols
// shake out of a plain-data bundle? Generates a baseline core (fns name their
// slot) and a registry core (fns name no slot), bundles a plain-data app that
// uses several fns against each, and reports bytes + how many slot symbols
// survive. Needs esbuild (this repo's devDep).
//
//   node dce.js

const { buildSync } = require('esbuild');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'adr7-dce-'));
const slots = ['ILookup', 'IAssoc', 'IColl', 'ICount', 'IMap', 'IEquiv', 'IIndexed', 'IReset', 'ISwap', 'IWatch', 'IDeref', 'IEmpty'];

// baseline: 12 slot symbols; 5 fns each naming a few, so a broad app pins all 12
let base = slots.map((s) => `export const ${s}__s = /* @__PURE__ */ Symbol('${s}_-s');`).join('\n') + '\n' +
  `export function get(c,k,nf){ if(c==null)return nf; if(c.constructor===Object){const v=c[k];return v===undefined?nf:v;} if(c[ILookup__s]!==undefined)return c[ILookup__s](c,k,nf); if(c[IIndexed__s]!==undefined)return c[IIndexed__s](c,k); return nf; }
export function assoc(c,k,v){ if(c[IAssoc__s]!==undefined)return c[IAssoc__s](c,k,v); if(c[IMap__s]!==undefined){} return {...c,[k]:v}; }
export function conj(c,x){ if(c[IColl__s]!==undefined)return c[IColl__s](c,x); if(c[IEmpty__s]!==undefined){} return [...c,x]; }
export function deref(a){ if(a[IDeref__s]!==undefined)return a[IDeref__s](a); return a.v; }
export function swap(a,f){ if(a[ISwap__s]!==undefined)return a[ISwap__s](a,f); a.v=f(a.v); if(a[IWatch__s]!==undefined){} if(a[IReset__s]!==undefined){} if(a[IEquiv__s]!==undefined){} if(a[ICount__s]!==undefined){} return a.v; }`;

// registry: same fns, native inline, no slot symbols; registry only for extend-type
let reg = `const reg=new Map();
function dispatch(p,c){ const m=reg.get(p); return m&&m.get(c.constructor); }
export function extendType(p,T,fn){ (reg.get(p)||reg.set(p,new Map()).get(p)).set(T,fn); }
export function get(c,k,nf){ if(c==null)return nf; if(c.constructor===Object){const v=c[k];return v===undefined?nf:v;} if(reg.size){const f=dispatch('lookup',c);if(f)return f(c,k,nf);} return nf; }
export function assoc(c,k,v){ if(reg.size){const f=dispatch('assoc',c);if(f)return f(c,k,v);} return {...c,[k]:v}; }
export function conj(c,x){ if(reg.size){const f=dispatch('coll',c);if(f)return f(c,x);} return [...c,x]; }
export function deref(a){ if(reg.size){const f=dispatch('deref',a);if(f)return f(a);} return a.v; }
export function swap(a,f){ if(reg.size){const g=dispatch('swap',a);if(g)return g(a,f);} a.v=f(a.v); return a.v; }`;

fs.writeFileSync(path.join(dir, 'core-base.js'), base);
fs.writeFileSync(path.join(dir, 'core-reg.js'), reg);
const app = `import {get,assoc,conj,deref,swap} from CORE; const a={v:0}; console.log(get({x:1},'x'), assoc({},'k',1), conj([],1), deref(a), swap(a,n=>n+1));`;

for (const [name, core] of [['baseline (slots)', 'core-base.js'], ['registry', 'core-reg.js']]) {
  const entry = path.join(dir, 'app.js');
  fs.writeFileSync(entry, app.replace('CORE', JSON.stringify('./' + core)));
  const out = buildSync({ entryPoints: [entry], bundle: true, minify: true, format: 'esm', write: false, absWorkingDir: dir }).outputFiles[0].text;
  const symbolsLeft = (out.match(/_-s/g) || []).length;
  console.log(name.padEnd(18), 'bytes=' + out.length, ' slot-symbols-remaining=' + symbolsLeft);
}
fs.rmSync(dir, { recursive: true, force: true });
