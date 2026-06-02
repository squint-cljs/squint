# Browser REPL: open TODOs

Short list pulled from `notes/internal/browser-repl-dep-resolution.md`. Each item meant to be tackled in its own context.

## Eval / printing

- **`(try ..)` at top level**: still flattens a user Promise because emit-try wraps the form in an async IIFE before the outer `:repl-return` boxes it. Workaround: wrap in `(let [r (try ..)] r)`.
- **stdout / err streaming**: route browser `console.log` / errors back to nREPL `out` + `err` (with stacktrace + ex-message), not only the final return value.
- **Interrupt / load-file** over browser transport. Currently only `eval` op routed.

## Sessions

- **Multi-session routing**: `!browser-send`, `!pending`, `last-ns`, `state` in `lib/node.nrepl_server.js` are global. One editor/tab assumed. Route by `session`; decide multi-tab model (one tab = one session? session picks tab?).

## Deps

- **Auto-derive `optimizeDeps.include`** from source `:require`s, so known npm deps pre-bundle (no first-use reload). Option (1) in dep-resolution notes.
- **Shadow-style on-demand bundling** (option 3): esbuild-bundle any REPL-required dep on the fly, serve at stable URL, resolve via registry. Principled end-state, zero reloads.

## Docs

- CJS deps return under `.default` (e.g. `(.. canvas-confetti -default)`). Document.
- HMR self-accept re-runs module side effects (listeners re-bind). Document gotcha.
