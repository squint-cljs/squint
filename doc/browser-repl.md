# Browser REPL

The squint vite plugin (`squint-cljs/vite`) hot-reloads your squint code in the
browser and lets you eval into the live page over nREPL. The following setup
runs it via `npm run dev`.

The squint vite plugin currently supports vite v5-v8.

For a working setup, see [`examples/browser-repl`](../examples/browser-repl).

## Setup

`squint.edn`:

```edn
{:paths ["src"]
 :output-dir "js"
 :extension "js"
 :main plain}
```

`vite.config.js`:

```js
import { defineConfig } from 'vite';
import squint from 'squint-cljs/vite';

export default defineConfig({ plugins: [squint()] });
```

`index.html` (the plugin injects the `:main` entry ns):

```html
<div id="plain"></div>
```

`package.json`:

```json
{
  "type": "module",
  "scripts": {
    "dev": "vite dev",
    "build": "vite build"
  }
}
```

`npm run dev` compiles `:paths`, serves the app, and starts the nREPL server
(port `1339`, written to `.nrepl-port`).

`npm run build` produces a normal optimized bundle with regular (non-REPL) squint
output, with the dev-only REPL/HMR stripped.

## React / Preact (JSX)

Set `:jsx-runtime` so squint emits `jsx()`/`jsxs()` calls (importing the
framework's runtime) instead of raw `<tags>`. This is what makes `#jsx` work at
the REPL and in the browser - the output is plain JS, with no separate JSX
transform step.

```edn
{:paths ["src"]
 :output-dir "js"
 :extension "js"
 :main [plain preact reagami-app]
 :jsx-runtime {:import-source "preact"}} ; or "react"
```

The plugin uses the dev runtime (`<import-source>/jsx-dev-runtime`) under
`vite dev` and the production runtime (`<import-source>/jsx-runtime`) for
`vite build`.

See `examples/browser-repl/src/preact.cljs` for a working component.

## Reagami (no JSX)

[Reagami](https://github.com/borkdude/reagami) is a zero-dep, Reagent-like
hiccup renderer. It renders plain hiccup vectors (`[:div ...]`), so no
`:jsx-runtime` and no `#jsx` - it works at the REPL out of the box. State is a
plain atom with `add-watch` + `render`. See
`examples/browser-repl/src/reagami_app.cljs`.

## Options

| key | meaning |
|---|---|
| `:main` | entry ns whose compiled module the plugin injects as a `<script>` into `index.html`, booting the app (so the output path isn't hardcoded); symbol/string, or a vector for several |
| `:paths` | source dirs (default `["src"]`) |
| `:output-dir` | output dir (default `"js"`) |
| `:extension` | output extension (default `"js"`) |
| `:nrepl-port` | nREPL port (default `1339`) |
| `:target` | runtime target (only `browser`) |
| `:jsx-runtime` | `{:import-source "react"\|"preact"}` to emit jsx-runtime calls for JSX (see above) |

## Optional: pre-bundle deps for a smoother REPL

The first time you require a dependency vite hasn't seen, vite pre-bundles it and
reloads the page, which causes loss of REPL state. To avoid this, list the deps
you reach for at the REPL in vite's `optimizeDeps.include`:

```js
export default defineConfig({
  optimizeDeps: {
    include: ['preact', 'preact/hooks', 'preact/jsx-runtime', 'preact/jsx-dev-runtime'],
  },
  plugins: [squint()],
});
```

Hopefully a better solution for this will be implemented later.
