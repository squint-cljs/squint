# Browser nREPL: production TODO

Tracking work to make the browser REPL (branch `browser-repl-on-main`) production grade.

## How it works today

- `squint watch --repl` compiles cljs -> js, binds top-level defs to `globalThis.<ns>.<name>`.
- `vite dev` serves the browser app.
- `squint nrepl-server --target browser --port 13333` runs the nREPL TCP server (editor) plus a second WebSocketServer (browser).
- Browser loads `js/nrepl.js` -> opens `ws://host:<port>/_nrepl`.
- Editor eval -> TCP -> server compiles cljs -> JS pushed over WS -> browser evals -> result back over WS -> server -> editor.

Files: `src/squint/repl/nrepl_server.cljs`, `src/squint/nrepl.js` (browser client), `examples/browser-repl/`.

## Phase 1: make it work end-to-end

- [ ] `nrepl.js`: `ex: ex.toString` is missing the call, sends the function not the message. Fix to `ex.toString()` (or `ex.message`).
- [ ] Result value uses `res?.toString()`. Print properly (pr-str), handle nil/undefined/objects.
- [ ] Port model: two ports today (nREPL TCP, WS). WS port is hardcoded 1340; `VITE_SQUINT_NREPL_PORT` is misnamed and matches only by luck. Make the WS port explicit/derived and document it.
- [ ] Remove debug noise: `prn ::data`, `println "About to eval"`, `console.log` in client.
- [ ] Example: duplicate watcher. `vite.config.js` buildStart spawns `squint watch --repl` and the `dev` script also runs it via concurrently. Pick one.
- [ ] Confirm `:port`/`:target` coercion in `cli.cljs` (port likely arrives as string).

## Phase 2: robustness

- [ ] WS auto-reconnect on close/error (tab reload, server restart).
- [ ] Error/stacktrace formatting: send `err` + ex-message, not just `ex`.
- [ ] Stream stdout/print from browser to editor (`out` messages).
- [ ] Graceful handling when no browser is connected (error to client, not just a server-side println).

## Phase 3: full production grade

- [ ] Multi-session routing: map nREPL session -> ws connection. Today `!ws-conn`/`!response-handler` are single global atoms; multiple tabs/editors clobber.
- [ ] Per-session ns/state instead of global `last-ns`/`state` atoms.
- [ ] Interrupt and load-file ops over WS.
- [ ] Tests (repo has playwright; drive a browser + nREPL client end-to-end).

## DX / cleanup

- [ ] `.nrepl-port` is committed in the example; gitignore it.
- [ ] WS port should not require the vite env var; provide a fallback.
- [ ] README for `examples/browser-repl` (how to start, how to connect an editor).
