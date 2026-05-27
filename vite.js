// squint browser REPL over vite's HMR WebSocket.
//
// Design (compare with the standalone-WebSocketServer approach on the
// browser-repl-on-main branch): there is no second WebSocket server. REPL eval
// rides vite's own dev-server WS (import.meta.hot), so reconnection and the
// module graph are owned by vite and stay consistent with hot reload.
//
// The plugin:
// - compiles cljs -> js in-process via squint's compileFile API (compile all
//   on startup, recompile changed files via vite's watcher; one-shot for build),
// - runs squint's own nREPL server (squint-cljs/lib/node.nrepl_server.js) and
//   injects a browser transport, so editors connect over bencode TCP and eval
//   is delegated to the browser over vite's WS. Same nREPL impl as `squint
//   nrepl-server`; no second copy of bencode/ops.
// - injects a browser-side eval listener (import.meta.hot) speaking the nREPL
//   server's eval message format.
//
// A dev-only HTTP endpoint (POST /__repl_eval) drives the same eval path with
// curl, handy for testing without an editor.

import { compileFile, readConfig } from './node-api.js';
import {
  startServer,
  handleBrowserMessage,
  evalString,
} from './lib/node.nrepl_server.js';
import { readdirSync, existsSync } from 'node:fs';
import { join, resolve, sep } from 'node:path';

const VIRTUAL_CLIENT = 'virtual:squint-repl-client';
const RESOLVED_CLIENT = '\0' + VIRTUAL_CLIENT;
const CLJS_RE = /\.clj[sc]$/;

// Browser-side listener, served as a virtual module so the page imports it and
// gets a real import.meta.hot context. Speaks the nREPL server's eval format:
// receives {op:"eval", code, id, session}, replies {op:"eval", value/ex, id, session}.
function clientCode() {
  return `
if (import.meta.hot) {
  import.meta.hot.on('squint:nrepl', async ({ op, code, id, session }) => {
    if (op !== 'eval') return;
    // bare dynamic imports in eval'd code go through vite's resolver
    const rewritten = code.replace(/import\\('(.+?)'\\)/g, "import('/@resolve-deps/$1')");
    let value, ex;
    try {
      value = await eval(rewritten);
    } catch (e) {
      ex = e && e.message ? e.message : String(e);
    }
    import.meta.hot.send('squint:nrepl-reply', {
      op: 'eval',
      id,
      session,
      value: value === undefined ? 'nil' : String(value),
      ...(ex ? { ex } : {}),
    });
  });
  console.info('[squint-repl] nrepl listener ready');
}
`;
}

export default function squint(options = {}) {
  // Where the REPL runtime lives. Only :browser today; the option leaves room
  // for a node/SSR target (vite is used for node dev too) without a rename.
  const target = options.target ?? 'browser';
  if (target !== 'browser') {
    throw new Error(
      `squint vite plugin: target ${JSON.stringify(target)} not supported yet (only 'browser')`,
    );
  }
  const nreplPort =
    options.nreplPort ??
    (process.env.SQUINT_NREPL_PORT ? Number(process.env.SQUINT_NREPL_PORT) : 1339);

  let root;
  let isBuild = false;
  let logger = console;
  // Resolved in configResolved from squint.edn (plugin options override it).
  // `paths` are absolute source dirs.
  let paths = [];
  let outDir = 'js';
  let extension = 'js';

  function compileCljs(file) {
    return compileFile({
      'in-file': file,
      'output-dir': join(root, outDir),
      paths,
      extension,
      repl: true,
    });
  }

  async function compileAll() {
    for (const dir of paths) {
      let entries;
      try {
        entries = readdirSync(dir, { recursive: true, withFileTypes: true });
      } catch {
        continue;
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
  }

  return {
    name: 'squint-repl',

    configResolved(config) {
      root = config.root;
      isBuild = config.command === 'build';
      logger = config.logger ?? console;
      // squint.edn is the source of truth; plugin options override it.
      const cfg = readConfig(root) || {};
      paths = (options.paths ?? cfg.paths ?? ['src']).map((p) => resolve(root, p));
      outDir = options.outDir ?? cfg['output-dir'] ?? 'js';
      extension = options.extension ?? cfg.extension ?? 'js';
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

      const onChange = async (file) => {
        const abs = resolve(file);
        if (!CLJS_RE.test(abs) || !paths.some((p) => abs.startsWith(p + sep))) return;
        try {
          await compileCljs(abs);
          logger.info('[squint-repl] compiled ' + abs);
        } catch (err) {
          logger.error('[squint-repl] compile error in ' + abs + ': ' + (err.message || err));
        }
      };
      for (const p of paths) server.watcher.add(p);
      server.watcher.on('change', onChange);
      server.watcher.on('add', onChange);

      // Resolve bare specifiers used in REPL-eval'd dynamic import()s. npm/path
      // specifiers go through vite's resolver; a bare ns name (e.g. `index`,
      // which squint emits for a local cljs require) that vite can't resolve
      // falls back to its compiled output under outDir (e.g. /js/index.js).
      server.middlewares.use('/@resolve-deps', async (req, res) => {
        const spec = decodeURIComponent(req.url.slice(1));
        let resolved = null;
        try {
          resolved = await server.moduleGraph.resolveId(spec);
        } catch {
          resolved = null;
        }
        if (resolved && resolved.id) {
          res.writeHead(302, { location: `/@fs${resolved.id}` });
          res.end();
          return;
        }
        const rel = outDir + '/' + spec.replace(/\./g, '/') + '.' + extension;
        if (existsSync(join(root, rel))) {
          res.writeHead(302, { location: '/' + rel });
          res.end();
          return;
        }
        res.writeHead(404);
        res.end();
      });

      // Start squint's nREPL server, delegating eval to the browser over vite's
      // WS. Browser replies come back on 'squint:nrepl-reply'.
      server.ws.on('squint:nrepl-reply', (data) => handleBrowserMessage(data));
      await startServer({
        port: nreplPort,
        browserTransport: { send: (msg) => server.ws.send('squint:nrepl', msg) },
      });
      logger.info('[squint-repl] nREPL server on port ' + nreplPort);

      // Dev HTTP trigger: drive the same eval path with curl (no editor needed).
      server.middlewares.use('/__repl_eval', async (req, res) => {
        if (req.method !== 'POST') {
          res.writeHead(405);
          res.end();
          return;
        }
        let body = '';
        for await (const chunk of req) body += chunk;
        res.writeHead(200, { 'content-type': 'application/json' });
        try {
          const out = await evalString(body);
          res.end(JSON.stringify({ value: out.value, ns: out.ns }));
        } catch (e) {
          res.end(JSON.stringify({ err: e && e.message ? e.message : String(e) }));
        }
      });
    },
  };
}
