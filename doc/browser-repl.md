# Browser REPL

The squint vite plugin (`squint-cljs/vite`) hot-reloads your squint code in the
browser and lets you eval into the live page over nREPL. The following setup
runs it via `npm run dev`.

For a working setup, see [`examples/browser-repl`](../examples/browser-repl).

## Setup

`squint.edn`:

```edn
{:paths ["src"]
 :output-dir "js"
 :extension "js"
 :main index}
```

`vite.config.js`:

```js
import { defineConfig } from 'vite';
import squint from 'squint-cljs/vite';

export default defineConfig({ plugins: [squint()] });
```

`index.html` (the plugin injects the `:main` entry ns):

```html
<div id="app"></div>
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

## Npm deps at the REPL

Npm dependencies work out of the box. But the first time you require one in developmental, vite
pre-bundles it and reloads the page, which means you'll lose your REPL state. To avoid that you can
pre-bundle the deps you expect to use:

```js
defineConfig({ optimizeDeps: { include: ['canvas-confetti', 'nanoid'] }, plugins: [squint()] })
```

## React / Preact (JSX)

Set `:jsx-runtime` so squint emits `jsx()`/`jsxs()` calls (importing the
framework's runtime) instead of raw `<tags>`. This is what makes `#jsx` work at
the REPL and in the browser - the output is plain JS, with no separate JSX
transform step.

```edn
{:paths ["src"]
 :output-dir "js"
 :extension "js"
 :main [index app]
 :jsx-runtime {:import-source "preact"}} ; or "react"
```

The plugin uses the dev runtime (`<source>/jsx-dev-runtime`) under `vite dev`
and the production runtime (`<source>/jsx-runtime`) for `vite build`. Pre-bundle
the runtime so the first REPL render doesn't reload the page:

```js
optimizeDeps: { include: ['preact', 'preact/jsx-runtime', 'preact/jsx-dev-runtime'] }
```

See `examples/browser-repl/src/app.cljs` for a working component.

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
