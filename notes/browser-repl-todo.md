# Browser REPL: production TODO

Two approaches explored:

- **standalone-WS** (`browser-repl-on-main`): squint runs its own WebSocketServer; browser opens a separate WS. Decoupled from vite, but two channels (vite HMR + REPL WS) drift, and we own reconnect/routing.
- **vite-HMR** (`browser-repl-vite`): REPL eval rides vite's own dev-server WS (`import.meta.hot`). vite owns the socket, reconnect, and the module graph, so REPL and hot reload stay consistent. **Chosen direction.**

This file tracks the vite-HMR branch. Standalone-WS issues kept at the bottom for reference.

## How it works now (vite-HMR)

- `vite dev` is the only command. `vite-plugin-squint-repl.js` owns everything:
  - compiles cljs -> js in-process via squint's `compileFile` API (compile all on startup, recompile changed files via vite's file watcher; one-shot compile in `buildStart` for builds).
  - injects `import.meta.hot.accept()` into served compiled modules (dev) so a recompile hot-swaps the module, no page reload. `globalThis.<ns>` state and REPL defs survive.
  - injects a virtual-module browser listener (`import.meta.hot.on('squint:eval')`).
  - eval transport: compiles a REPL form with `compileStringEx` (state threaded), pushes JS over `server.ws`, awaits `squint:eval-result`.
- Eval is triggered over a dev-only HTTP endpoint (`POST /__repl_eval`) for now, so it can be driven with curl before a real nREPL relay exists.

Files: `examples/browser-repl/vite-plugin-squint-repl.js`, `examples/browser-repl/vite.config.js`.

## Done (vite-HMR branch)

- [x] Eval round-trip over vite's HMR WS (`(+ 1 2)` -> 3, state threads).
- [x] Plugin owns the compile pipeline via squint API (no subprocess, no `squint` on PATH).
- [x] True HMR: recompile hot-swaps modules, no full page reload, state survives.
- [x] Dropped the standalone WebSocketServer, the misnamed port env, the duplicate watcher, and the separate nrepl-server process.

## Next: dependency resolution (current focus)

- [ ] eval'd code does `await import('joi')` / `import('squint-cljs/core.js')` at runtime. Bare specifiers can't load in the browser without help. Today: client regex-rewrites `import('x')` -> `import('/@resolve-deps/x')`, and a vite middleware 302-redirects to `/@fs/...`.
- [ ] Decide the real mechanism: keep the `/@resolve-deps` middleware, precompute an import map, or use vite's own resolver more directly.
- [ ] Test: require a new npm dep at the REPL that the app didn't already import; require another cljs ns; relative imports.

## Then: real nREPL relay (replace HTTP trigger)

- [ ] Speak bencode nREPL over TCP so editors (Calva/CIDER) connect; bridge to `server.ws`. Reuse squint's bencode/ops or write a thin relay in the plugin.
- [ ] Write `.nrepl-port` for editor auto-discovery.

## REPL semantics (helps both branches)

- [ ] Cross-ns refs: `index/hello` compiles to bare `index.hello`, not `globalThis.index.hello`, so referencing another ns from the REPL fails.
- [ ] Result printing: use pr-str, handle nil/undefined/objects (currently `String(value)`).

## Robustness

- [ ] Error/stacktrace formatting: send `err` + ex-message.
- [ ] Stream stdout/print from browser to editor (`out` messages).
- [ ] Multiple browser tabs / multiple editor sessions: today the plugin broadcasts to all ws clients and matches results by id. Decide session model.

## DX / cleanup

- [ ] Remove unused `concurrently` dep and the now-dead standalone client files (`src/nrepl.cljs`, `js/nrepl.js`, `src/squint/nrepl.js`) on this branch.
- [ ] `.nrepl-port` committed in the example; gitignore it.
- [ ] README for `examples/browser-repl` (how to start, how to connect an editor).
- [ ] Self-accept re-runs module side effects (fine for the demo's re-render; document for apps with listeners).

## Standalone-WS leftover issues (reference only)

- [ ] `nrepl.js`: `ex: ex.toString` missing the call.
- [ ] WS port hardcoded 1340; `VITE_SQUINT_NREPL_PORT` misnamed.
- [ ] `prn ::data` / `println "About to eval"` debug noise.
- [ ] Single global `!ws-conn`/`!response-handler`/`last-ns`/`state` atoms.
