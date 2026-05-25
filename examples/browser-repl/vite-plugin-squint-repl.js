// squint browser REPL over vite's HMR WebSocket, with the squint
// compile pipeline owned by this plugin via squint's JS API.
//
// Design (compare with the standalone-WebSocketServer approach on the
// browser-repl-on-main branch): there is no second WebSocket server. REPL
// eval rides vite's own dev-server WS (import.meta.hot), so reconnection and
// the module graph are owned by vite and stay consistent with hot reload.
//
// The plugin compiles cljs -> js in-process with squint's compileFile API
// (no subprocess, no `squint` on PATH). In dev it compiles once on startup
// and recompiles changed files via vite's own file watcher; for a production
// build it compiles everything in buildStart. So `vite dev` / `vite build`
// is the only command you need.
//
// For now eval is triggered over a dev-only HTTP endpoint (POST /__repl_eval)
// so we can drive it with curl before wiring a real nREPL/editor relay.

import { compileStringEx } from 'squint-cljs';
import { compileFile } from 'squint-cljs/node-api.js';
import { randomUUID } from 'node:crypto';
import { readdirSync } from 'node:fs';
import { join, resolve, sep } from 'node:path';

const VIRTUAL_CLIENT = 'virtual:squint-repl-client';
const RESOLVED_CLIENT = '\0' + VIRTUAL_CLIENT;
const CLJS_RE = /\.clj[sc]$/;

// Browser-side listener, served as a virtual module so the page imports it
// and gets a real import.meta.hot context.
function clientCode() {
  return `
if (import.meta.hot) {
  import.meta.hot.on('squint:eval', async ({ id, code }) => {
    // bare dynamic imports in eval'd code go through vite's resolver
    const rewritten = code.replace(/import\\('(.+?)'\\)/g, "import('/@resolve-deps/$1')");
    let value, err;
    try {
      value = await eval(rewritten);
    } catch (e) {
      err = e && e.message ? e.message : String(e);
    }
    import.meta.hot.send('squint:eval-result', {
      id,
      value: value === undefined ? 'nil' : String(value),
      err,
    });
  });
  console.info('[squint-repl] eval listener ready');
}
`;
}

export default function squintRepl(options = {}) {
  // Defaults mirror squint.edn (:paths, :output-dir, :extension).
  const srcDir = options.srcDir ?? 'src';
  const outDir = options.outDir ?? 'js';
  const extension = options.extension ?? 'js';

  // REPL compiler state, threaded across evals so ns/defs accumulate.
  let state = {};
  const pending = new Map();
  let root;
  let isBuild = false;
  let logger = console;

  function srcAbs() {
    return join(root, srcDir);
  }

  function compileCljs(file) {
    return compileFile({
      'in-file': file,
      'output-dir': join(root, outDir),
      paths: [srcAbs()],
      extension,
      repl: true,
    });
  }

  async function compileAll() {
    const dir = srcAbs();
    let entries;
    try {
      entries = readdirSync(dir, { recursive: true, withFileTypes: true });
    } catch {
      return;
    }
    for (const e of entries) {
      if (e.isFile() && CLJS_RE.test(e.name)) {
        const file = join(e.parentPath ?? e.path, e.name);
        try {
          await compileCljs(file);
        } catch (err) {
          logger.error('[squint-repl] compile error in ' + file + ': ' + (err.message || err));
        }
      }
    }
  }

  return {
    name: 'squint-repl',

    configResolved(config) {
      root = config.root;
      isBuild = config.command === 'build';
      logger = config.logger ?? console;
    },

    // Production build: compile everything before vite bundles.
    async buildStart() {
      if (isBuild) await compileAll();
    },

    resolveId(id) {
      if (id === VIRTUAL_CLIENT) return RESOLVED_CLIENT;
    },
    load(id) {
      if (id === RESOLVED_CLIENT) return clientCode();
    },

    // Make compiled cljs->js modules self-accepting in dev so a recompile
    // hot-swaps the module (re-runs it, re-binding globalThis.<ns> with the
    // new code) instead of triggering a full page reload. Injected at serve
    // time only, so the files on disk and the production build stay clean.
    transform(code, id) {
      if (isBuild) return;
      const file = id.split('?')[0];
      const outBase = join(root, outDir) + sep;
      if (file.startsWith(outBase) && file.endsWith('.' + extension)) {
        return code + '\nif (import.meta.hot) { import.meta.hot.accept(); }\n';
      }
    },
    transformIndexHtml() {
      return [
        {
          tag: 'script',
          // vite serves virtual modules under /@id/, encoding the leading
          // null byte of the resolved id as __x00__
          attrs: { type: 'module', src: '/@id/__x00__' + VIRTUAL_CLIENT },
          injectTo: 'head',
        },
      ];
    },

    async configureServer(server) {
      // Compile once on startup, then recompile changed cljs via vite's watcher.
      await compileAll();

      const base = srcAbs() + sep;
      const onChange = async (file) => {
        const abs = resolve(file);
        if (!CLJS_RE.test(abs) || !abs.startsWith(base)) return;
        try {
          await compileCljs(abs);
          logger.info('[squint-repl] compiled ' + abs);
        } catch (err) {
          logger.error('[squint-repl] compile error in ' + abs + ': ' + (err.message || err));
        }
      };
      server.watcher.add(srcAbs());
      server.watcher.on('change', onChange);
      server.watcher.on('add', onChange);

      // REPL eval transport over vite's HMR WS.
      server.ws.on('squint:eval-result', (data) => {
        const resolveFn = pending.get(data.id);
        if (resolveFn) {
          pending.delete(data.id);
          resolveFn(data);
        }
      });

      server.middlewares.use('/__repl_eval', async (req, res) => {
        if (req.method !== 'POST') {
          res.writeHead(405);
          res.end();
          return;
        }
        let body = '';
        for await (const chunk of req) body += chunk;

        let js;
        try {
          const out = compileStringEx(
            body,
            { repl: true, context: 'return', elide_exports: true, async: true },
            state,
          );
          state = out;
          js = `(async function () {\n${out.javascript}\n})()`;
        } catch (e) {
          res.writeHead(400, { 'content-type': 'application/json' });
          res.end(JSON.stringify({ err: 'compile error: ' + (e.message || String(e)) }));
          return;
        }

        if (server.ws.clients.size === 0) {
          res.writeHead(503, { 'content-type': 'application/json' });
          res.end(JSON.stringify({ err: 'no browser connected' }));
          return;
        }

        const id = randomUUID();
        const result = new Promise((resolveFn) => {
          pending.set(id, resolveFn);
          setTimeout(() => {
            if (pending.has(id)) {
              pending.delete(id);
              resolveFn({ id, err: 'timeout: no response from browser (10s)' });
            }
          }, 10000);
        });

        server.ws.send('squint:eval', { id, code: js });
        const out = await result;
        res.writeHead(200, { 'content-type': 'application/json' });
        res.end(JSON.stringify({ value: out.value, err: out.err, ns: state.ns }));
      });
    },
  };
}
