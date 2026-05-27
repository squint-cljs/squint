# Browser REPL

Edit ClojureScript, see it hot-reload in the browser, and connect your editor's
nREPL to evaluate forms in the **live page** - state and all.

It's a single vite plugin, `squint-cljs/vite`, so `vite dev` is the only command
you run. A complete working setup lives in
[`examples/browser-repl`](../examples/browser-repl).

## What you get

- **Compile**: squint compiles `.cljs` -> `.js` (watched in dev, built for `vite build`). No separate `squint watch`.
- **Hot reload**: a recompile hot-swaps the module, no page reload; `def`s and app state survive.
- **nREPL**: an nREPL server starts with the dev server. Connect Calva / CIDER / Conjure and eval in the running page. Cross-ns refs see redefinitions live.

## Setup

1. **Install** squint and vite:

   ```
   npm install squint-cljs vite
   ```

2. **`squint.edn`** - your source layout:

   ```edn
   {:paths ["src"]
    :output-dir "js"
    :extension "js"}
   ```

3. **`vite.config.js`** - add the plugin and name your entry ns:

   ```js
   import { defineConfig } from 'vite';
   import squint from 'squint-cljs/vite';

   export default defineConfig({
     plugins: [squint({ main: 'index' })],
   });
   ```

4. **`index.html`** - just a mount point; the plugin injects the entry ns:

   ```html
   <div id="app"></div>
   ```

5. **Run it:**

   ```
   npx vite dev
   ```

That compiles `src/**/*.cljs`, serves the app, and starts the nREPL server
(default port `1339`, written to `.nrepl-port`).

## Connect your editor

Point your editor's nREPL client at the running server:

- It writes `.nrepl-port` in the project dir, so editors that auto-discover it
  (Calva, CIDER, Conjure) connect with no extra config.
- Or connect manually to `localhost:1339`.

**Keep a browser tab open** at the dev URL while you use the REPL. Eval runs *in
the page*, so with no tab loaded an eval has nowhere to run and will error /
time out.

Then evaluate forms - they run in the browser page. Edit a `.cljs` file and it
hot-reloads; redefine a var and other namespaces that reference it see the new
value.

## Requiring npm deps at the REPL

vite pre-bundles only the deps it sees imported at startup. A dep you first
`(require ...)` at the REPL is discovered mid-session, which makes vite
re-optimize and **reload the page** (losing REPL state). Pre-bundle deps you
expect to use at the REPL via `optimizeDeps.include`:

```js
export default defineConfig({
  optimizeDeps: { include: ['lodash', 'nanoid'] },
  plugins: [squint()],
});
```

(CommonJS deps land under `.default`, e.g. `(.. lodash -default (add 1 2))`.)

## Plugin options

All optional. The source layout is read from `squint.edn`
(`:paths`/`:output-dir`/`:extension`); these options override it.

```js
squint({
  main: 'index',     // entry ns(s) to inject into index.html; string or array
  target: 'browser', // default; where the REPL runtime lives (only :browser for now)
  paths: ['src'],    // overrides squint.edn :paths
  outDir: 'js',      // overrides squint.edn :output-dir
  extension: 'js',   // overrides squint.edn :extension
  nreplPort: 1339,   // default; or set SQUINT_NREPL_PORT
})
```
