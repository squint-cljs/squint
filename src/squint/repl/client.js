// Browser client for the squint browser REPL in "generic" (non-vite) mode. A
// bundler (webpack, ...) bundles this via the generated repl_deps manifest;
// `connect({url})` opens a WebSocket to the plugin, speaks the nREPL server's
// eval message format, and resolves eval-time deps through the page registry, a
// session cache, or on-demand server bundling.
// See notes/internal/browser-repl-webpack-design.md.
//
// Wire format (JSON over WS):
//   server -> page : {op:"eval", code, id, session}
//                    {op:"complete-js", prefix, id, session}
//   plugin -> page : {op:"load", code, beforeLoad, afterLoad}  (:hot :ws)
//                    {op:"dep", id, code|error}                 (dep reply)
//   page -> server : {op:"eval", id, session, value, ex?}
//                    {op:"complete-js", id, session, completions}
//                    {op:"dep", spec, id}                       (dep request)

import { pr_str } from 'squint-cljs/core.js';

// Render a top-level Promise as #<Promise <value>> by racing it against a short
// timeout; pending/rejected get their own forms. Mirrors pr-str-repl in
// squint.repl.nrepl-server-common so node and browser eval print the same.
const PROMISE_PRINT_TIMEOUT_MS = 1000;
async function pr_str_repl(v) {
  if (!(v instanceof Promise)) return pr_str(v);
  const settled = v.then(
    (r) => ({ tag: 'resolved', val: r }),
    (e) => ({ tag: 'rejected', val: e }),
  );
  const timer = new Promise((resolve) =>
    setTimeout(() => resolve({ tag: 'pending' }), PROMISE_PRINT_TIMEOUT_MS),
  );
  const r = await Promise.race([settled, timer]);
  if (r.tag === 'pending') return '#<Promise pending>';
  if (r.tag === 'rejected') return '#<Promise rejected ' + pr_str(r.val) + '>';
  return '#<Promise ' + pr_str(r.val) + '>';
}

// JS-interop completion against the page's globalThis. Mirrors js-completions
// in squint.repl.nrepl-server-common (node side) so browser and node match.
function __jsCompletions(prefix) {
  if (!prefix || !prefix.startsWith('js/')) return [];
  const s = prefix.slice(3);
  const parts = s.split('.');
  const partial = parts[parts.length - 1];
  const path = parts.slice(0, -1);
  let obj = globalThis;
  for (const seg of path) { obj = obj == null ? obj : obj[seg]; }
  if (obj == null) return [];
  const acc = new Set();
  for (let o = obj; o != null; o = Object.getPrototypeOf(o)) {
    for (const n of Object.getOwnPropertyNames(o)) acc.add(n);
  }
  const pre = 'js/' + (path.length ? path.join('.') + '.' : '');
  return Array.from(acc).filter((n) => n.startsWith(partial)).sort().slice(0, 100).map((n) => pre + n);
}

// A raw import(url) the bundler won't rewrite: webpack/vite wrap a literal
// import() to route it through their loaders, which would break blob: urls and
// the resolveModule indirection. Building it via Function keeps the url exact.
const __rawImport = new Function('u', 'return import(u)');

export function connect({ url }) {
  const ws = new WebSocket(url);
  const depCache = new Map();   // session tier-2: spec -> module namespace
  const depPending = new Map(); // dep request id -> { resolve, reject }
  let depSeq = 0;

  function requestDep(spec) {
    return new Promise((resolve, reject) => {
      const id = 'dep-' + (++depSeq);
      depPending.set(id, { resolve, reject });
      ws.send(JSON.stringify({ op: 'dep', spec, id }));
    });
  }

  // Resolve a bare specifier for REPL-eval'd code: 1. page registry (shared
  // instances), 2. session cache, 3. server bundles on demand over the WS.
  async function resolveModule(spec) {
    const reg = globalThis.__squint_deps;
    if (reg && spec in reg) return reg[spec];
    if (depCache.has(spec)) return depCache.get(spec);
    const code = await requestDep(spec);
    const blobUrl = URL.createObjectURL(new Blob([code], { type: 'text/javascript' }));
    try {
      const mod = await __rawImport(blobUrl);
      depCache.set(spec, mod);
      return mod;
    } finally {
      URL.revokeObjectURL(blobUrl);
    }
  }
  // The rewritten eval'd code calls this by a stable global name (direct eval
  // can't rely on the bundler keeping resolveModule's lexical binding).
  globalThis.__squint_replImport = resolveModule;

  // bare dynamic imports in eval'd code resolve through resolveModule. \s*
  // tolerates the compiler emitting e.g. `import ('preact')` for :refer.
  function rewrite(code) {
    return code.replace(/import\s*\(\s*'(.+?)'\s*\)/g, "globalThis.__squint_replImport('$1')");
  }

  async function rawEval(code) {
    return await eval(rewrite(code));
  }

  // Resolve a dotted globalThis path to a fn and call it (dev hooks). Mirrors
  // the vite plugin's __<name>_dev_hook lookup.
  function callHooks(paths) {
    for (const p of paths || []) {
      let f = globalThis;
      for (const seg of p.split('.')) f = f == null ? f : f[seg];
      if (typeof f === 'function') {
        try { f(); } catch (e) { console.error('[squint-repl] dev hook ' + p + ' threw', e); }
      } else {
        console.warn('[squint-repl] dev hook not found: ' + p);
      }
    }
  }

  ws.addEventListener('message', async (event) => {
    let msg;
    try { msg = JSON.parse(event.data); } catch { return; }
    const { op, id, session } = msg;

    if (op === 'dep') {
      const p = depPending.get(id);
      if (!p) return;
      depPending.delete(id);
      if (msg.error) p.reject(new Error(msg.error));
      else p.resolve(msg.code);
      return;
    }

    if (op === 'complete-js') {
      ws.send(JSON.stringify({ op: 'complete-js', id, session, completions: __jsCompletions(msg.prefix) }));
      return;
    }

    if (op === 'load') {
      // :hot :ws - the plugin ships recompiled repl-mode output (already
      // stripped of exports and wrapped in an async IIFE); eval it between the
      // dev hooks. Cross-ns refs resolve through globalThis at call time, so
      // the stale bundled module is never consulted again.
      callHooks(msg.beforeLoad);
      try {
        await rawEval(msg.code);
      } catch (e) {
        console.error('[squint-repl] hot load failed', e);
        return;
      }
      callHooks(msg.afterLoad);
      console.info('[squint-repl] hot loaded');
      return;
    }

    if (op === 'eval') {
      let value, ex;
      try {
        // compile wraps the user's top-level value in [v] so a Promise survives
        // the async IIFE without being auto-unwrapped; unbox before printing.
        const boxed = await rawEval(msg.code);
        value = await pr_str_repl(boxed[0]);
      } catch (e) {
        ex = e && e.message ? e.message : String(e);
      }
      ws.send(JSON.stringify({ op: 'eval', id, session, value, ...(ex ? { ex } : {}) }));
      return;
    }
  });

  ws.addEventListener('open', () => console.info('[squint-repl] nrepl listener ready (ws ' + url + ')'));
  ws.addEventListener('error', (e) => console.error('[squint-repl] ws error', e));
  return ws;
}
