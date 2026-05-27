# Browser REPL

Hot-reload squint code in the browser and eval into the live page over nREPL,
via a single vite plugin (`squint-cljs/vite`). `vite dev` is the only command.

Working setup: [`examples/browser-repl`](../examples/browser-repl).

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

`npx vite dev` compiles `:paths`, serves the app, and starts the nREPL server
(port `1339`, written to `.nrepl-port`).

## Connect

Connect your editor to `.nrepl-port` (Calva/CIDER/Conjure auto-discover it) or
`localhost:1339`. Edit a `.cljs` and it hot-reloads; redefs are visible across
namespaces.

**Keep a browser tab open** at the dev URL - eval runs in the page.

## npm deps at the REPL

A dep first required mid-session makes vite re-optimize and reload the page
(losing state). Pre-bundle expected REPL deps:

```js
defineConfig({ optimizeDeps: { include: ['lodash', 'nanoid'] }, plugins: [squint()] })
```

CommonJS deps land under `.default` (`(.. lodash -default (add 1 2))`).

## Options

Set in `squint.edn` (kebab-case). Plugin options override; `SQUINT_NREPL_PORT`
overrides the port.

| squint.edn | plugin option | meaning |
|---|---|---|
| `:main` | `main` | entry ns(s) to inject; symbol/string or vector |
| `:paths` | `paths` | source dirs (default `["src"]`) |
| `:output-dir` | `outDir` | output dir (default `"js"`) |
| `:extension` | `extension` | output extension (default `"js"`) |
| `:nrepl-port` | `nreplPort` | nREPL port (default `1339`) |
| `:target` | `target` | runtime target (only `browser`) |
