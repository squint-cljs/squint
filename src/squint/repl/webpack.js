// Squint browser-REPL webpack plugin: the "generic mode" of the browser-nREPL
// design (notes/internal/browser-repl-webpack-design.md), hosted by a webpack
// plugin. `new SquintPlugin()` in webpack.config.js + `webpack serve` gives the
// browser nREPL without vite: compile cljs -> js (repl mode) in-process,
// generate a dep manifest and inject it into the app entry, run the nREPL
// server with a WebSocket transport, and hot-reload cljs over the WS (:hot :ws).
//
// POC scope: eval, the 3 dep-resolution tiers, and :hot :ws reload. Not here:
// the HMR self-accept footer (bundler-HMR mode), production polish, security
// token.

import { readdirSync, existsSync, writeFileSync, mkdirSync, readFileSync } from 'node:fs';
import { join, resolve, sep } from 'node:path';
import { watch as chokidarWatch } from 'chokidar';
import { compileFile, readConfig, depsPaths } from '../../../node-api.js';
import { startServer, handleBrowserMessage } from '../../../lib/node.nrepl_server.js';
import { makeBundleDep } from './deps.js';

const CLJS_RE = /\.clj[sc]$/;
const LOG = '[squint-repl]';

// Strip ESM export statements and wrap in an async IIFE so the repl-mode file
// output can be eval'd in the page for :hot :ws (eval can't hold top-level
// export/await). The client rewrites the `await import(...)` lines through
// resolveModule, and the globalThis.<ns> assignments re-run, rebinding vars.
function loadCode(js) {
  const body = js
    .replace(/^\s*export\s*\{[^}]*\}\s*;?\s*$/gm, '')
    .replace(/^\s*export\s+default\s+.*$/gm, '');
  return '(async function () {\n' + body + '\n})()';
}

// Manifest dep list: scanned from the cljs/cljc sources with a regex, not the
// compiler ns-state. The node API's compileFile does not expose the string
// requires as a clean list (they're folded into the emitted import text and
// into :aliases whose values mix npm specs with local JS paths), so a source
// scan is the pragmatic POC choice (the design's "cljam source scan"). Matches a
// string that is the first element of a require vector (followed by :as / :refer
// / :default / :rename / closing bracket), which excludes plain data vectors of
// strings. Limitation: dynamic/computed requires and requires not written as
// `["spec" ...]` are missed; those still resolve via tier 3 at eval time.
const REQUIRE_RE = /\[\s*"([^"]+)"\s*(?::as|:refer|:default|:rename|\])/g;

export class SquintPlugin {
  constructor(options = {}) {
    this.options = options;
    this._started = false;
  }

  apply(compiler) {
    const options = this.options;
    const isProd = compiler.options.mode === 'production';
    const root = compiler.options.context || process.cwd();
    const cfg = readConfig(root) || {};

    const depDirs = options.paths ? [] : depsPaths(root);
    const paths = [...(options.paths ?? cfg.paths ?? ['src']), ...depDirs].map((p) =>
      resolve(root, p),
    );
    const outDir = options.outDir ?? cfg['output-dir'] ?? 'js';
    const outAbs = resolve(root, outDir);
    const extension = options.extension ?? cfg.extension ?? 'js';
    const jsxRuntime = options.jsxRuntime ?? cfg['jsx-runtime'];
    // POC defaults avoid the vite plugin's 1339/1340: nREPL 1341, WS 1342.
    // env wins over the config file (a runtime override, e.g. for tests/CI),
    // options over env, mirroring the vite plugin.
    const nreplPort =
      options.nreplPort ??
      (process.env.SQUINT_NREPL_PORT ? Number(process.env.SQUINT_NREPL_PORT) : undefined) ??
      cfg['nrepl-port'] ??
      1341;
    const wsPort =
      options.wsPort ??
      (process.env.SQUINT_WS_PORT ? Number(process.env.SQUINT_WS_PORT) : undefined) ??
      cfg['ws-port'] ??
      1342;
    const debug = options.debug ?? cfg.debug ?? false;

    // Shared compiler ns-state: a cljs atom captured from the first compile and
    // threaded into every later compile and the nREPL server, so the REPL knows
    // file-defined vars/aliases. Survives the JS boundary as an opaque object.
    let nsState;
    // source file -> { before-load, after-load }: munged globalThis paths of
    // ^:dev/before-load / ^:dev/after-load fns, refreshed per compile.
    const devHooks = new Map();

    async function compileCljs(file) {
      const res = await compileFile({
        'in-file': file,
        'output-dir': outAbs,
        paths,
        extension,
        // REPL output (globalThis bindings, dynamic imports) in dev; regular,
        // optimizable ESM for a production build.
        repl: !isProd,
        ...(jsxRuntime ? { 'jsx-runtime': { ...jsxRuntime, development: !isProd } } : {}),
        ...(isProd ? {} : { 'ns-state': nsState }),
      });
      if (!isProd) {
        nsState = res['ns-state'];
        devHooks.set(file, res['dev-hooks']);
      }
      return res;
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
              console.error(LOG + ' compile error in ' + file + ': ' + (err.message || err));
            }
          }
        }
      }
    }

    // ------------------------------------------------------------- manifest ---

    function scanDeps() {
      const specs = new Set();
      for (const dir of paths) {
        let entries;
        try {
          entries = readdirSync(dir, { recursive: true, withFileTypes: true });
        } catch {
          continue;
        }
        for (const e of entries) {
          if (!(e.isFile() && CLJS_RE.test(e.name))) continue;
          const src = readFileSync(join(e.parentPath ?? e.path, e.name), 'utf8');
          let m;
          REQUIRE_RE.lastIndex = 0;
          while ((m = REQUIRE_RE.exec(src))) {
            // strip a $suffix (the compiler splits "lodash$default" -> import
            // 'lodash'); skip relative/local specifiers (they resolve on disk).
            const spec = m[1].split('$')[0];
            if (spec.startsWith('.') || spec.startsWith('/')) continue;
            specs.add(spec);
          }
        }
      }
      return [...specs];
    }

    function writeManifest() {
      const specs = scanDeps();
      // #jsx compiles to imports of the jsx runtime (compiler-injected, so the
      // source scan can't see them); register both variants so REPL-eval'd jsx
      // shares the page's runtime (and its preact) instead of a tier-3 copy.
      if (jsxRuntime && jsxRuntime['import-source']) {
        for (const rt of ['/jsx-runtime', '/jsx-dev-runtime']) {
          const spec = jsxRuntime['import-source'] + rt;
          if (!specs.includes(spec)) specs.push(spec);
        }
      }
      const imports = [];
      const reg = [];
      // Register squint core so REPL-eval'd `await import('squint-cljs/core.js')`
      // shares the page's one core instance (protocol/atom identity) instead of
      // bundling a second copy via tier 3.
      imports.push("import * as __squint_core from 'squint-cljs/core.js';");
      reg.push("'squint-cljs/core.js': __squint_core");
      specs.forEach((s, i) => {
        imports.push('import * as m' + i + ' from ' + JSON.stringify(s) + ';');
        reg.push(JSON.stringify(s) + ': m' + i);
      });
      const code =
        imports.join('\n') +
        '\nglobalThis.__squint_deps = { ' + reg.join(', ') + ' };\n' +
        "import { connect } from 'squint-cljs/repl-client';\n" +
        "connect({ url: 'ws://localhost:" + wsPort + "' });\n";
      mkdirSync(outAbs, { recursive: true });
      const file = join(outAbs, 'repl_deps.js');
      writeFileSync(file, code);
      return file;
    }

    // ------------------------------------------------------------ transports --

    const sockets = new Set();
    const bundleDep = makeBundleDep({ root });

    function broadcast(msg) {
      const data = JSON.stringify(msg);
      for (const s of sockets) if (s.readyState === 1) s.send(data);
    }

    async function startWs() {
      let WebSocketServer;
      try {
        // ws is CJS: its default export is the WebSocket class, with the server
        // as a static (WebSocketServer named export isn't exposed to ESM).
        const ws = await import('ws');
        WebSocketServer = ws.WebSocketServer ?? ws.default.Server;
      } catch {
        throw new Error('ws is required for the squint webpack REPL (npm i ws)');
      }
      const wss = new WebSocketServer({ host: '127.0.0.1', port: wsPort });
      wss.on('connection', (sock, req) => {
        // The socket accepts eval and browsers allow cross-site WS handshakes,
        // so refuse pages not served from localhost (cross-site WS hijacking).
        // A missing Origin header (non-browser client) is allowed.
        const origin = req.headers.origin;
        if (origin && !/^https?:\/\/(localhost|127\.0\.0\.1|\[::1\])(:\d+)?$/.test(origin)) {
          sock.close(1008, 'origin not allowed');
          return;
        }
        sockets.add(sock);
        sock.on('close', () => sockets.delete(sock));
        sock.on('message', async (raw) => {
          let msg;
          try {
            msg = JSON.parse(raw.toString());
          } catch {
            return;
          }
          if (msg.op === 'dep') {
            // tier 3: bundle the spec on demand and reply on this socket.
            try {
              const code = await bundleDep(msg.spec);
              sock.send(JSON.stringify({ op: 'dep', id: msg.id, code }));
            } catch (e) {
              bundleDep.cache.delete(msg.spec);
              sock.send(JSON.stringify({ op: 'dep', id: msg.id, error: e && e.message ? e.message : String(e) }));
            }
            return;
          }
          // eval / complete-js replies feed back into the nREPL server.
          handleBrowserMessage(msg);
        });
      });
      console.log(LOG + ' ws transport on port ' + wsPort);
    }

    async function startNrepl() {
      await startServer({
        port: nreplPort,
        debug,
        browserTransport: {
          send: broadcast,
          // best effort for the "Open <url> in a browser tab" timeout hint;
          // nil is handled (generic hint) when there is no dev server config.
          url: () => {
            const dev = compiler.options.devServer;
            if (!dev) return undefined;
            return 'http://localhost:' + (dev.port ?? 8080) + '/';
          },
        },
        nsState,
      });
      console.log(LOG + ' nREPL server on port ' + nreplPort);
    }

    function startWatch() {
      const watcher = chokidarWatch(paths, { ignoreInitial: true });
      const onChange = async (file) => {
        const abs = resolve(file);
        if (!CLJS_RE.test(abs)) return;
        try {
          const res = await compileCljs(abs);
          const hooks = res['dev-hooks'] || {};
          broadcast({
            op: 'load',
            code: loadCode(res.javascript),
            beforeLoad: hooks['before-load'] || [],
            afterLoad: hooks['after-load'] || [],
          });
          console.log(LOG + ' compiled + hot-loaded ' + abs);
        } catch (e) {
          console.error(LOG + ' compile error in ' + abs + ': ' + (e.message || e));
        }
      };
      watcher.on('change', onChange);
      watcher.on('add', onChange);
      watcher.on('unlink', (f) => devHooks.delete(resolve(f)));
    }

    // -------------------------------------------------------------- lifecycle --

    if (isProd) {
      // Production: compile once (no repl mode, no server, no injected entry).
      compiler.hooks.beforeCompile.tapPromise('SquintPlugin', async () => {
        if (this._started) return;
        this._started = true;
        await compileAll();
      });
      return;
    }

    // Inject the manifest into every entrypoint as a global entry (a new chunk
    // would never be loaded by pages that reference fixed bundle files). The
    // manifest is scanned+written synchronously here so it resolves when webpack
    // reads modules. EntryPlugin with no name prepends it to all entrypoints.
    const manifestFile = writeManifest();
    const { EntryPlugin } = compiler.webpack;
    new EntryPlugin(compiler.options.context, manifestFile, { name: undefined }).apply(compiler);

    // Start-once guard covers multi-config / repeated applies. compileAll must
    // finish before webpack reads the (compiled) app modules, so run it in the
    // awaited beforeCompile hook.
    compiler.hooks.beforeCompile.tapPromise('SquintPlugin', async () => {
      if (this._started) return;
      this._started = true;
      await compileAll();
      await startWs();
      await startNrepl();
      startWatch();
    });
  }
}

export default SquintPlugin;
