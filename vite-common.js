// Browser REPL over vite's HMR WebSocket, shared between squint and cherry
// (see cherry ADR 0004).
//
// Design (compare with the standalone-WebSocketServer approach on the
// browser-repl-on-main branch): there is no second WebSocket server. REPL eval
// rides vite's own dev-server WS (import.meta.hot), so reconnection and the
// module graph are owned by vite and stay consistent with hot reload.
//
// The plugin:
// - compiles cljs -> js in-process via the dialect's compileFile API (compile
//   all on startup, recompile changed files via vite's watcher; one-shot for
//   build),
// - runs the dialect's nREPL server and injects a browser transport, so
//   editors connect over bencode TCP and eval is delegated to the browser over
//   vite's WS. Same nREPL impl as `<dialect> nrepl-server`; no second copy of
//   bencode/ops.
// - injects a browser-side eval listener (import.meta.hot) speaking the nREPL
//   server's eval message format.
//
// An optional dev-only HTTP endpoint (POST /__repl_eval, off by default, see
// ENABLE_HTTP_EVAL) drives the same eval path with curl, handy without an
// editor.
//
// The adapter supplies the dialect:
// {
//   name,                 // 'squint' | 'cherry': derives virtual module id,
//                         // WS event names, log prefixes, env var, dev hook
//   coreImport,           // core module specifier for the browser client,
//                         // e.g. 'squint-cljs/core.js'; must export pr_str
//   compileFile, readConfig, depsPaths,          // node API fns
//   startServer, handleBrowserMessage, evalString, // nREPL server fns
// }

import { readdirSync, existsSync, readFileSync } from 'node:fs';
import { join, resolve, relative, isAbsolute, sep } from 'node:path';

const CLJS_RE = /\.clj[sc]$/;

// Dev-only HTTP eval endpoint (POST /__repl_eval), handy for driving eval with
// curl when there's no editor. Off by default (an open dev server would expose
// arbitrary in-page eval); flip to true in-source when you need it.
const ENABLE_HTTP_EVAL = false;

// Browser-side listener, served as a virtual module so the page imports it and
// gets a real import.meta.hot context. Speaks the nREPL server's eval format:
// receives {op:"eval", code, id, session}, replies {op:"eval", value/ex, id, session}.
function clientCode({ name, coreImport, evt, evtReply, log }) {
  return `
import { pr_str } from '${coreImport}';
// Render a top-level Promise as #<Promise <value>> by racing it against a
// short timeout; pending/rejected get their own readable forms. Mirrors
// pr-str-repl in squint.repl.nrepl-server-common so node and browser eval
// print the same thing.
const PROMISE_PRINT_TIMEOUT_MS = 1000;
async function pr_str_repl(v) {
  if (!(v instanceof Promise)) return pr_str(v);
  const settled = v.then(r => ({ tag: 'resolved', val: r }),
                         e => ({ tag: 'rejected', val: e }));
  const timer = new Promise(resolve =>
    setTimeout(() => resolve({ tag: 'pending' }), PROMISE_PRINT_TIMEOUT_MS));
  const r = await Promise.race([settled, timer]);
  if (r.tag === 'pending') return '#<Promise pending>';
  if (r.tag === 'rejected') return '#<Promise rejected ' + pr_str(r.val) + '>';
  return '#<Promise ' + pr_str(r.val) + '>';
}
// Resolve a bare specifier to the exact url vite serves it at, then import THAT
// url. We can't \`import('/@resolve-deps/preact')\` and let the endpoint redirect:
// the browser's module map keys by the *request* url, so a redirected import is
// a different map entry than the page's own \`import('/node_modules/.vite/deps/
// preact.js?v=...')\` - two preact instances, two \`options\` registries, and a
// REPL \`render\` then can't reconcile the page's existing tree. Resolving first
// and importing the canonical url shares the one instance.
// Indirect import so vite's import-analysis doesn't rewrite it: a literal
// \`import(url)\` here gets wrapped as \`import(__vite__injectQuery(url,'import'))\`,
// which appends &import to the url - a different string than the page's own
// \`import('/node_modules/.vite/deps/preact.js?v=...')\`, hence a second module
// instance. Building the import() via Function keeps the url byte-for-byte.
const __rawImport = new Function('u', 'return import(u)');
async function __replImport(spec) {
  const res = await fetch('/@resolve-deps/' + encodeURIComponent(spec));
  if (!res.ok) {
    // append the server's body (esbuild's diagnosis: unresolvable node builtin,
    // missing package, ...) so the user sees WHY, not just "could not resolve".
    const detail = await res.text().catch(() => '');
    throw new Error('${name}-repl: could not resolve ' + spec + (detail ? ': ' + detail : ''));
  }
  const url = await res.text();
  return __rawImport(url);
}
// JS-interop completion against the page's globalThis. Mirrors js-completions
// in squint.repl.nrepl-server-common (node side) so browser and node behave
// the same.
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
if (import.meta.hot) {
  import.meta.hot.on('${evt}', async ({ op, code, id, session, prefix }) => {
    if (op === 'complete-js') {
      import.meta.hot.send('${evtReply}', {
        op: 'complete-js', id, session, completions: __jsCompletions(prefix),
      });
      return;
    }
    if (op !== 'eval') return;
    // bare dynamic imports in eval'd code resolve through __replImport
    // (\\s* tolerates the compiler emitting e.g. \`import ('preact')\` for :refer)
    const rewritten = code.replace(/import\\s*\\(\\s*'(.+?)'\\s*\\)/g, "__replImport('$1')");
    let value, ex;
    try {
      // compile wraps the user's top-level value in [v] so a Promise survives
      // the async IIFE without being auto-unwrapped; unbox before printing.
      const boxed = await eval(rewritten);
      value = await pr_str_repl(boxed[0]);
    } catch (e) {
      ex = e && e.message ? e.message : String(e);
    }
    import.meta.hot.send('${evtReply}', {
      op: 'eval',
      id,
      session,
      value,
      ...(ex ? { ex } : {}),
    });
  });
  console.info('${log} nrepl listener ready');
}
`;
}

export function makeVitePlugin(adapter) {
  const {
    name,
    coreImport,
    compileFile,
    readConfig,
    depsPaths,
    startServer,
    handleBrowserMessage,
    evalString,
  } = adapter;
  const PLUGIN_NAME = `${name}-repl`;
  const VIRTUAL_CLIENT = `virtual:${name}-repl-client`;
  const RESOLVED_CLIENT = '\0' + VIRTUAL_CLIENT;
  const EVT = `${name}:nrepl`;
  const EVT_REPLY = `${name}:nrepl-reply`;
  const LOG = `[${PLUGIN_NAME}]`;
  const ENV_PORT = `${name.toUpperCase()}_NREPL_PORT`;
  const DEV_HOOK = `__${name}_dev_hook`;

  return function plugin(options = {}) {
    let root;
    let isBuild = false;
    let logger = console;
    // All settings come from the config file (plugin options override, then env
    // for the nREPL port). Resolved in configResolved (needs `root`).
    // `paths` are absolute source dirs.
    let paths = [];
    let outDir = 'js';
    let extension = 'js';
    let main; // entry ns(s): string or array, injected into index.html
    let target = 'browser';
    let nreplPort = 1339;
    let debug = false;
    // {import-source} when set (e.g. for React/Preact): the compiler emits
    // jsx()/jsxs() calls + imports the runtime, instead of raw <tags> a bundler
    // would have to transform. We flip :development per mode (dev -> jsx-dev-runtime).
    let jsxRuntime; // JS object from the config file's :jsx-runtime, or undefined
    // Shared compiler ns-state, threaded through every file compile and handed to
    // the nREPL server, so the REPL knows the vars/aliases the files defined (a
    // cljs atom; survives the JS boundary as an opaque object). Dev only.
    let nsState;
    // source file -> {before-load: [...], after-load: [...]}: munged globalThis
    // paths of ^:dev/before-load / ^:dev/after-load fns, refreshed per compile,
    // dropped when the source is deleted.
    const devHooks = new Map();

    async function compileCljs(file) {
      const res = await compileFile({
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
        // thread the shared ns-state (dev only; the build doesn't need a REPL)
        ...(isBuild ? {} : { 'ns-state': nsState }),
      });
      if (!isBuild) {
        nsState = res['ns-state']; // capture/refresh the shared atom
        devHooks.set(file, res['dev-hooks']);
      }
      return res;
    }

    function hookCalls(which) {
      const paths = [...devHooks.values()].flatMap((h) => (h ? h[which] : []));
      return paths.map((p) => `${DEV_HOOK}(${JSON.stringify(p)});`).join(' ');
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
              logger.error(LOG + ' compile error in ' + file + ': ' + (err.message || err));
            }
          }
        }
      }
    }

    return {
      name: PLUGIN_NAME,

      configResolved(config) {
        root = config.root;
        isBuild = config.command === 'build';
        logger = config.logger ?? console;
        // the config file is the source of truth; plugin options override it.
        const cfg = readConfig(root) || {};
        // :deps in the config file resolve (via `clojure -Spath`) to absolute
        // source dirs; add them to paths so the dep namespaces are compiled and
        // their requires resolve. depsPaths reads the config file itself: the
        // deps map has symbol keys that would not survive readConfig's
        // clj<->js round-trip.
        const depDirs = options.paths ? [] : depsPaths(root);
        paths = [...(options.paths ?? cfg.paths ?? ['src']), ...depDirs].map((p) =>
          resolve(root, p),
        );
        outDir = options.outDir ?? cfg['output-dir'] ?? 'js';
        extension = options.extension ?? cfg.extension ?? 'js';
        main = options.main ?? cfg.main;
        target = options.target ?? cfg.target ?? 'browser';
        jsxRuntime = options.jsxRuntime ?? cfg['jsx-runtime'];
        // env wins over the config file (a runtime override, e.g. for tests/CI)
        nreplPort =
          options.nreplPort ??
          (process.env[ENV_PORT] ? Number(process.env[ENV_PORT]) : undefined) ??
          cfg['nrepl-port'] ??
          1339;
        debug = options.debug ?? cfg.debug ?? false;
        if (target !== 'browser') {
          throw new Error(
            `${name} vite plugin: target ${JSON.stringify(target)} not supported yet (only 'browser')`,
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
        if (id === RESOLVED_CLIENT) {
          return clientCode({ name, coreImport, evt: EVT, evtReply: EVT_REPLY, log: LOG });
        }
      },

      // Make compiled cljs->js modules self-accepting in dev so a recompile
      // hot-swaps the module (re-runs it, re-binding globalThis.<ns> with the
      // new code) instead of triggering a full page reload. ^:dev/before-load
      // hooks run on dispose (old module about to be replaced), ^:dev/after-load
      // hooks on accept (new module has executed). Hooks resolve by name on
      // globalThis at call time. Injected at serve time only, so the files on
      // disk and the production build stay clean.
      transform(code, id) {
        if (isBuild) return;
        const file = id.split('?')[0];
        const outBase = join(root, outDir) + sep;
        if (file.startsWith(outBase) && file.endsWith('.' + extension)) {
          const before = hookCalls('before-load');
          const after = hookCalls('after-load');
          const noHooksHint =
            `console.info('[${name}] hot reloaded, but no ^:dev/after-load hook is defined to e.g. re-render');`;
          return (
            code +
            `
if (import.meta.hot) {
  globalThis.${DEV_HOOK} ??= (p) => {
    let f = globalThis;
    for (const seg of p.split('.')) f = f?.[seg];
    if (typeof f !== 'function') { console.warn('[${name}] dev hook not found: ' + p); return; }
    try { f(); } catch (e) { console.error('[${name}] dev hook ' + p + ' threw', e); }
  };
  ${before ? `import.meta.hot.dispose(() => { ${before} });` : ''}
  import.meta.hot.accept(() => { ${after || noHooksHint} });
}
`
          );
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
          // hardcode the output path. ns -> file uses the compiler's munging.
          // Relative src so `vite build` treats it as a bundle input (absolute =
          // public asset).
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
            logger.info(LOG + ' compiled ' + abs);
          } catch (err) {
            logger.error(LOG + ' compile error in ' + abs + ': ' + (err.message || err));
          }
        };
        for (const p of paths) server.watcher.add(p);
        server.watcher.on('change', onChange);
        server.watcher.on('add', onChange);
        server.watcher.on('unlink', (file) => devHooks.delete(resolve(file)));

        // Resolve a bare specifier from REPL-eval'd dynamic import()s to the url
        // vite serves it at, returned as text (the client imports that url; see
        // __replImport in the injected client - it must import the canonical
        // url, not a redirect, to share the page's module instance). npm/path
        // specifiers go through vite's resolver; a bare ns name (e.g. `index`,
        // which the compiler emits for a local cljs require) that vite can't
        // resolve falls back to its compiled output under outDir (e.g. /js/index.js).
        const sendUrl = (res, url) => {
          res.writeHead(200, { 'content-type': 'text/plain' });
          res.end(url);
        };

        // vite's dep-optimizer metadata: which bare specs it has already
        // pre-bundled (`optimized`) or discovered mid-session (`discovered`).
        // Prefer the live in-memory optimizer (vite 6+); fall back to the
        // on-disk _metadata.json for older vite. Shape:
        //   { browserHash, optimized: { "<spec>": { file, fileHash, ... } } }
        // (on disk `file` is relative to the deps dir, e.g. "nanoid.js"; live it
        // is an absolute path - callers take the basename).
        const optimizerMetadata = () => {
          const live = server.environments?.client?.depsOptimizer?.metadata;
          if (live) return live;
          try {
            const p = join(server.config.cacheDir, 'deps', '_metadata.json');
            return JSON.parse(readFileSync(p, 'utf8'));
          } catch {
            return null;
          }
        };
        // Does vite's optimizer already know this bare spec? If so we can safely
        // resolveId it (it won't trigger a re-optimize) and share the page's
        // instance. If not, resolveId would REGISTER it as a missing dep and
        // schedule a re-optimize + full page reload - so unknown bare specs must
        // never reach the resolver (see the middleware below).
        const optimizerKnows = (spec) => {
          const md = optimizerMetadata();
          if (!md) return false;
          return !!((md.optimized && md.optimized[spec]) ||
                    (md.discovered && md.discovered[spec]));
        };

        // esbuild plugin: when an on-demand bundle imports a transitive bare dep
        // vite has ALREADY optimized (e.g. an app-shared preact), don't inline a
        // second copy - point it at the page's optimized url so the browser
        // reuses the one module instance (same identity-sharing reasoning as the
        // canonical-url path above). If metadata is unreadable, inline it
        // (silently) - a second copy of a transitive is acceptable.
        const viteOptimizedExternal = {
          name: 'vite-optimized-external',
          setup(build) {
            build.onResolve({ filter: /^[^./]/ }, (args) => {
              const md = optimizerMetadata();
              const entry = md && md.optimized && md.optimized[args.path];
              if (!entry) return null;
              // cacheDir default <root>/node_modules/.vite -> url prefix
              // /node_modules/.vite/deps/... (the form the page's imports use).
              const rel = relative(root, server.config.cacheDir).split(sep).join('/');
              const base = entry.file.split(/[\\/]/).pop();
              return { external: true, path: '/' + rel + '/deps/' + base + '?v=' + md.browserHash };
            });
          },
        };

        // On-demand esbuild bundling of a brand-new npm dep (never seen by
        // vite). Cache: spec -> Promise<bundled esm text>, so concurrent
        // requests share one build and /@repl-deps can re-read the text.
        const replDepCache = new Map();
        const bundleDep = (spec) => {
          let p = replDepCache.get(spec);
          if (p) return p;
          p = (async () => {
            let esbuild;
            try {
              esbuild = await import('esbuild');
            } catch {
              throw new Error('esbuild is required for on-demand REPL deps (npm i esbuild)');
            }
            // A proxy module: esbuild's entryPoints treat the arg as a file
            // path, so bundle a stdin module that imports the spec instead, and
            // normalize CJS default-export interop (`import('lodash')` should
            // hand back the lodash object, not a { default } namespace).
            const proxy = `import * as __m from ${JSON.stringify(spec)};\n` +
              `export * from ${JSON.stringify(spec)};\n` +
              `export default (__m.default !== undefined ? __m.default : __m);`;
            const out = await esbuild.build({
              stdin: { contents: proxy, resolveDir: root, sourcefile: 'repl-dep-proxy.js' },
              bundle: true, format: 'esm', platform: 'browser', write: false,
              logLevel: 'silent',
              define: { 'process.env.NODE_ENV': '"development"' },
              plugins: [viteOptimizedExternal],
            });
            return out.outputFiles[0].text;
          })();
          replDepCache.set(spec, p);
          return p;
        };

        server.middlewares.use('/@resolve-deps', async (req, res) => {
          const spec = decodeURIComponent(req.url.slice(1));
          const isBare = !spec.startsWith('.') && !spec.startsWith('/') && !isAbsolute(spec);
          // The optimizer's reload trap is only for bare *package* specifiers
          // (extensionless, e.g. `lodash`, `firebase/app`) - those it wants to
          // pre-bundle. A bare spec with an explicit JS file extension (e.g.
          // `squint-cljs/core.js`, the REPL's own core import) resolves to a real
          // file vite serves directly, never through the optimizer - so resolveId
          // on it is safe and, crucially, shares the page's ONE instance (a
          // second esbuild-bundled core would break cross-instance atom/protocol
          // identity). Keep those on the resolver path.
          const isPkgSpec = isBare && !/\.[mc]?jsx?$/.test(spec);

          // A bare package spec vite's optimizer has never seen is the reload
          // trap: resolveId on it registers it as a missing dep and schedules a
          // re-optimize + full page reload (wiping REPL state). So for an unknown
          // bare package spec, DO NOT call the resolver - resolve it another way.
          if (isPkgSpec && !optimizerKnows(spec)) {
            // a bare cljs ns name (e.g. `index`, emitted by the compiler for a
            // local require) resolves to its compiled output, never esbuild.
            const rel = outDir + '/' + spec.replace(/\./g, '/') + '.' + extension;
            if (existsSync(join(root, rel))) {
              sendUrl(res, '/' + rel);
              return;
            }
            // genuinely-new npm dep: bundle on demand and serve it at a stable
            // /@repl-deps url vite never analyzes, so the optimizer never learns
            // of it (no re-optimize, no reload).
            try {
              await bundleDep(spec);
            } catch (e) {
              replDepCache.delete(spec);
              res.writeHead(404, { 'content-type': 'text/plain' });
              // esbuild's error names unresolvable node builtins (node:fs, ...)
              // and missing packages - that IS the browser-compat diagnosis.
              res.end(e && e.message ? e.message : String(e));
              return;
            }
            sendUrl(res, '/@repl-deps/' + encodeURIComponent(spec));
            return;
          }

          let resolved = null;
          // resolve via the plugin container (runs vite's own resolver + dep
          // pre-bundling). vite 6+ exposes it per-environment; older vite only on
          // the server. (server.moduleGraph.resolveId existed in vite <=5 but was
          // dropped in 6+, so don't depend on it.)
          const container =
            server.environments?.client?.pluginContainer ?? server.pluginContainer;
          try {
            resolved = await container.resolveId(spec);
          } catch {
            resolved = null;
          }
          if (resolved && resolved.id) {
            // Return the SAME url the page uses for this module. vite serves files
            // under `root` at a root-relative url and files outside it under /@fs;
            // an optimized dep (<root>/node_modules/.vite/deps/foo.js?v=HASH) must
            // come back as /node_modules/.vite/deps/foo.js?v=HASH - the form the
            // page's own imports resolve to - so the browser reuses the one module
            // instance (a different url string => a second copy => two `options`
            // registries => a REPL `render` can't reconcile the page's tree and
            // #jsx silently no-ops).
            const id = resolved.id;
            const url = id.startsWith(root + sep)
              ? id.slice(root.length)
              : `/@fs${id}`;
            sendUrl(res, url);
            return;
          }
          const rel = outDir + '/' + spec.replace(/\./g, '/') + '.' + extension;
          if (existsSync(join(root, rel))) {
            sendUrl(res, '/' + rel);
            return;
          }
          res.writeHead(404);
          res.end();
        });

        // Serve an on-demand-bundled dep. __replImport imports this via
        // __rawImport (a raw import(url)), so vite's import-analysis never sees
        // it and the browser module map keys it stably by this url - repeated
        // requires of the same dep share one instance.
        server.middlewares.use('/@repl-deps', async (req, res) => {
          const spec = decodeURIComponent(req.url.slice(1));
          const p = replDepCache.get(spec);
          if (!p) {
            res.writeHead(404);
            res.end();
            return;
          }
          try {
            const code = await p;
            res.writeHead(200, { 'content-type': 'text/javascript' });
            res.end(code);
          } catch (e) {
            replDepCache.delete(spec);
            res.writeHead(404, { 'content-type': 'text/plain' });
            res.end(e && e.message ? e.message : String(e));
          }
        });

        // Start the dialect's nREPL server, delegating eval to the browser over
        // vite's WS. Browser replies come back on the reply event.
        server.ws.on(EVT_REPLY, (data) => handleBrowserMessage(data));
        await startServer({
          port: nreplPort,
          debug,
          browserTransport: {
            send: (msg) => server.ws.send(EVT, msg),
            // resolved lazily: vite only knows its URL once it's listening
            url: () => server.resolvedUrls?.local?.[0] ?? server.resolvedUrls?.network?.[0],
          },
          // share the ns-state accumulated by the file compiles above, so the
          // REPL knows their vars/aliases (e.g. a def that ran at page load)
          nsState,
        });
        logger.info(LOG + ' nREPL server on port ' + nreplPort);

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
  };
}
