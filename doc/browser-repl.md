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

## Options

| key | meaning |
|---|---|
| `:main` | entry ns whose compiled module the plugin injects as a `<script>` into `index.html`, booting the app (so the output path isn't hardcoded); symbol/string, or a vector for several |
| `:paths` | source dirs (default `["src"]`) |
| `:output-dir` | output dir (default `"js"`) |
| `:extension` | output extension (default `"js"`) |
| `:nrepl-port` | nREPL port (default `1339`) |
| `:target` | runtime target (only `browser`) |
