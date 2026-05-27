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
// An optional dev-only HTTP endpoint (POST /__repl_eval, off by default, see
// ENABLE_HTTP_EVAL) drives the same eval path with curl, handy without an editor.

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

// Dev-only HTTP eval endpoint (POST /__repl_eval), handy for driving eval with
// curl when there's no editor. Off by default (an open dev server would expose
// arbitrary in-page eval); flip to true in-source when you need it.
const ENABLE_HTTP_EVAL = false;

// Browser-side listener, served as a virtual module so the page imports it and
// gets a real import.meta.hot context. Speaks the nREPL server's eval format:
// receives {op:"eval", code, id, session}, replies {op:"eval", value/ex, id, session}.
function clientCode() {
  return `
if (import.meta.hot) {
  import.meta.hot.on('squint:nrepl', async ({ op, code, id, session }) => {
    if (op !== 'eval') return;
    // bare dynamic imports in eval'd code go through vite's resolver
    // (\\s* tolerates squint emitting e.g. \`import ('preact')\` for :refer)
    const rewritten = code.replace(/import\\s*\\(\\s*'(.+?)'\\s*\\)/g, "import('/@resolve-deps/$1')");
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
  let root;
  let isBuild = false;
  let logger = console;
  // All settings come from squint.edn (plugin options override, then env for
  // the nREPL port). Resolved in configResolved (needs `root`).
  // `paths` are absolute source dirs.
  let paths = [];
  let outDir = 'js';
  let extension = 'js';
  let main; // entry ns(s): string or array, injected into index.html
  let target = 'browser';
  let nreplPort = 1339;
  // {import-source} when set (e.g. for React/Preact): squint emits jsx()/jsxs()
  // calls + imports the runtime, instead of raw <tags> a bundler would have to
  // transform. We flip :development per mode (dev -> jsx-dev-runtime).
  let jsxRuntime; // JS object from squint.edn :jsx-runtime, or undefined

  function compileCljs(file) {
    return compileFile({
      'in-file': file,
      'output-dir': join(root, outDir),
      paths,
      extension,
      // REPL output (globalThis bindings, dynamic imports) in dev; regular,
      // optimizable ESM for production builds.
      repl: !isBuild,
      // Dev uses the jsx-dev-runtime (better errors/source info); build uses
      // the production jsx-runtime.
      ...(jsxRuntime ? { 'jsx-runtime': { ...jsxRuntime, development: !isBuild } } : {}),
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
      main = options.main ?? cfg.main;
      target = options.target ?? cfg.target ?? 'browser';
      jsxRuntime = options.jsxRuntime ?? cfg['jsx-runtime'];
      // env wins over squint.edn (a runtime override, e.g. for tests/CI)
      nreplPort =
        options.nreplPort ??
        (process.env.SQUINT_NREPL_PORT ? Number(process.env.SQUINT_NREPL_PORT) : undefined) ??
        cfg['nrepl-port'] ??
        1339;
      if (target !== 'browser') {
        throw new Error(
          `squint vite plugin: target ${JSON.stringify(target)} not supported yet (only 'browser')`,
        );
      }
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
    transformIndexHtml: {
      // 'pre' so the injected entry script is collected as a build input
      // (vite's build-html scans scripts during its own transform).
      order: 'pre',
      handler() {
      const tags = [];
      // The REPL eval listener is dev-only (uses import.meta.hot); don't ship it.
      if (!isBuild) {
        tags.push({
          tag: 'script',
          // vite serves virtual modules under /@id/, encoding the leading
          // null byte of the resolved id as __x00__
          attrs: { type: 'module', src: '/@id/__x00__' + VIRTUAL_CLIENT },
          injectTo: 'head',
        });
      }
      // Inject the entry namespace's compiled module, so index.html doesn't
      // hardcode the output path. ns -> file uses squint's munging. Relative src
      // so `vite build` treats it as a bundle input (absolute = public asset).
      for (const ns of [].concat(main ?? [])) {
        const file = String(ns).replace(/-/g, '_').replace(/\./g, '/');
        tags.push({
          tag: 'script',
          attrs: { type: 'module', src: `${outDir}/${file}.${extension}` },
          injectTo: 'body',
        });
      }
      return tags;
      },
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
        browserTransport: {
          send: (msg) => server.ws.send('squint:nrepl', msg),
          // resolved lazily: vite only knows its URL once it's listening
          url: () => server.resolvedUrls?.local?.[0] ?? server.resolvedUrls?.network?.[0],
        },
      });
      logger.info('[squint-repl] nREPL server on port ' + nreplPort);

      // Dev HTTP trigger: drive the same eval path with curl (no editor needed).
      // Gated off by default; see ENABLE_HTTP_EVAL.
      if (ENABLE_HTTP_EVAL) {
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
      }
    },
  };
}
