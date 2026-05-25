# Browser REPL: production TODO

Two approaches explored:

- **standalone-WS** (`browser-repl-on-main`): squint runs its own WebSocketServer; browser opens a separate WS. Decoupled from vite, but two channels (vite HMR + REPL WS) drift, and we own reconnect/routing.
- **vite-HMR** (`browser-repl-vite`): REPL eval rides vite's own dev-server WS (`import.meta.hot`). vite owns the socket, reconnect, and the module graph, so REPL and hot reload stay consistent. **Chosen direction.**

This file tracks the vite-HMR branch. Standalone-WS issues kept at the bottom for reference.

## How it works now (vite-HMR)

- `vite dev` is the only command. `vite-plugin-squint-repl.js` owns everything:
  - compiles cljs -> js in-process via squint's `compileFile` API (compile all on startup, recompile changed files via vite's file watcher; one-shot compile in `buildStart` for builds).
  - injects `import.meta.hot.accept()` into served compiled modules (dev) so a recompile hot-swaps the module, no page reload. `globalThis.<ns>` state and REPL defs survive.
  - runs squint's own nREPL server (`squint-cljs/lib/node.nrepl_server.js`) with an injected browser transport. Editors connect over bencode TCP (port 1339, `.nrepl-port` written); the server compiles eval and pushes JS to the browser over `server.ws` (`squint:nrepl`); the browser evals and replies (`squint:nrepl-reply`), routed back by request id.
  - injects a virtual-module browser listener speaking that eval format.
- A dev-only HTTP endpoint (`POST /__repl_eval`) drives the same eval path with curl (no editor needed); shares the server's `evalString`, so one eval implementation.

Files: `examples/browser-repl/vite-plugin-squint-repl.js`, `examples/browser-repl/vite.config.js`.

## Done (vite-HMR branch)

- [x] Eval round-trip over vite's HMR WS (`(+ 1 2)` -> 3, state threads).
- [x] Plugin owns the compile pipeline via squint API (no subprocess, no `squint` on PATH).
- [x] True HMR: recompile hot-swaps modules, no full page reload, state survives.
- [x] Dropped the standalone WebSocketServer, the misnamed port env, the duplicate watcher, and the separate nrepl-server process.
- [x] Cross-ns requires resolve (node API auto-wires `:resolve-ns`; emits `./foo.js`).
- [x] Real nREPL: editors connect over bencode TCP and eval in the browser, reusing squint's own nREPL server via a transport seam (no second nREPL impl).

## Dependency resolution (punted, see browser-repl-dep-resolution.md)

Decision 2026-05-25: a page reload when a brand-new dep is first required at the
REPL is acceptable for now. Use `optimizeDeps.include` for known deps (no reload
for those). Revisit shadow-style on-demand bundling later.

- [x] Reproduced + root-caused the reload (vite re-optimizes new deps).
- [x] `optimizeDeps.include` proven to avoid reload for listed deps; REPL state survives.
- [ ] Maybe: auto-derive `optimizeDeps.include` from source `:require`s (cljam-style scan).
- [ ] Later: shadow-style on-demand esbuild bundling for zero-reload arbitrary deps.
- [ ] CJS interop nuance: CJS deps land under `.default` (`(.-default ld)`); also `optimizeDeps.needsInterop`.

## nREPL (done): transport-agnostic seam in squint's own server

Chose to reuse squint's `nrepl_server` rather than write a second nREPL impl in
JS. Made the server transport-pluggable instead of hardwiring how eval reaches
the runtime:

- `nrepl_server` now has an `!eval-fn` seam. Default is local `js/eval` (node).
  `start-server` accepts a `:browser-transport` (`{send}`); when present it uses
  `browser-eval`, which sends compiled JS to the browser and awaits the reply
  correlated by request id. `handle-browser-message` (exported) feeds replies in.
- Exposed `startServer`/`handleBrowserMessage`/`evalString` (shadow exports);
  `lib/node.nrepl_server.js` added to package exports so JS hosts can import it.
- The vite plugin injects a vite-WS transport. No bencode/op duplication.

Remaining nREPL polish:
- [ ] Multi-session: `!browser-send`/`!pending`/`last-ns`/`state` are global (single editor/tab). Route by session.
- [ ] Richer value printing (pr-str) + stdout streaming (`out`), error/stacktrace formatting from the browser.
- [ ] interrupt / load-file over the browser transport.

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
