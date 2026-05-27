# Browser REPL: dependency resolution design

Design notes for how REPL-eval'd code should resolve its npm/cljs dependencies
in the browser, without forcing a page reload. Remaining browser-repl TODOs
(beyond dep resolution) are at the bottom.

## Decision (2026-05-25): punt

A page reload when a brand-new dep is first required at the REPL is acceptable
for now. Use `optimizeDeps.include` for deps known up front (no reload for
those). Revisit the shadow-style on-demand bundling (option 3) later if the
reload becomes annoying. Rationale below.

## The problem

squint's REPL eval compiles a form to JS that does runtime `await import('pkg')`
(rewritten to `/@resolve-deps/pkg`, resolved by a vite middleware). When the dep
was not seen by vite at startup, that runtime import triggers vite's dep
optimizer:

```
[vite] ✨ new dependencies optimized: lodash
[vite] ✨ optimized dependencies changed. reloading
```

The page reloads. That wipes REPL state (`globalThis.<ns>` defs) and times out
the in-flight eval. Reproduced: requiring `lodash`/`nanoid` (not imported by the
app) reloaded; `joi` (imported by the app, so pre-bundled) did not.

Why: vite pre-bundles only the deps it sees in `import` statements while
crawling from `index.html` at startup. A dep first required at the REPL was
never in any such statement, so vite discovers it lazily at runtime and reloads.

## How other tools avoid it

### cljam (vite plugin, import map)

- At plugin init, scans every `:require ["pkg" :as X]` in all source files.
- Emits static `import * as _imp_0 from "pkg"` in a virtual module, so vite's
  startup scan sees them and pre-bundles them (no `optimizeDeps.include` needed).
- Builds `__importMap = { "pkg": _imp_0, ... }`. Eval'd code resolves deps via
  `importModule(s) => __importMap[s]` - a synchronous map lookup, never a
  runtime `import()`.
- Net: no runtime import -> vite never discovers a new dep -> never reloads.
- Tradeoff: a dep not in any source `:require` cannot be required at the REPL
  (errors "cannot require X"); you add a `:require` to source and recompile.

### shadow-cljs (own runtime module system, no bundler)

- No browser-native ESM, no bundler dev server. Uses Closure's ModuleManager +
  `shadow.js.require()` as its own runtime module registry.
- REPL eval and hot reload both inject server-compiled JS via
  `goog.globalEval()` into the live page.
- npm deps become "shim" resources; new ones are compiled into shims on demand
  mid-session, shipped over WS, eval'd, registered in the registry.
- The compiler/build state lives in a persistent server process.
- Net: loads arbitrary new code and new deps into a live page, never a reload,
  even for deps never seen before.

## The unifying insight

| | dep resolution at eval | new dep at REPL | reload? |
|---|---|---|---|
| cljam | static import-map lookup (from source scan) | no (edit source) | never |
| shadow | own runtime registry (`shadow.js.require`) | yes (server bundles on demand) | never |
| squint (now) | browser-native `import('pkg')` | yes | yes, every new dep |

cljam and shadow both **avoid browser-native `import()` for deps** - they
resolve from a runtime registry/map. squint reloads precisely because it uses
native `import()`, which hands control to vite's optimizer.

For *code*, squint is already shadow-like: eval + a `globalThis.<ns>` registry
for defs. The only gap is deps: squint reaches for native `import()` instead of
a registry.

## Options for squint

1. **`optimizeDeps.include`** (implemented as a test)
   - List deps (or auto-derive from source `:require`s) so vite pre-bundles them
     at startup. Source deps then never reload.
   - A brand-new REPL dep still reloads once.
   - Cheapest. Stays vite-coupled. Can't blindly include all of `package.json`
     (node-only tooling like `vite`/`concurrently` breaks the optimizer).

2. **cljam-style import map**
   - Scan source `:require`s, build a map, rework REPL eval to resolve deps from
     the map instead of `import()`.
   - No reload for source deps. No brand-new REPL deps without a source edit.
   - Requires changing how squint REPL eval emits dep access.

3. **shadow-style on-demand bundling** (most squint-native)
   - The REPL server esbuild-bundles any required dep on the fly (squint already
     ships esbuild), serves it at a stable URL; eval resolves from a registry.
   - Arbitrary new deps at the REPL, never a reload.
   - Matches squint's existing eval + globalThis-registry model.
   - Most work, but the principled end-state.

## Does vite offer JIT without reload?

vite's optimizer IS just-in-time (it bundled lodash/nanoid on demand), and it
has a no-reload commit path. The reload is conditional
(`vite/dist/node/chunks/dep-*.js`, `needsReload`):

```js
const needsReload =
  needsInteropMismatch.length > 0 ||           // a CJS interop assumption changed
  metadata.hash !== newData.hash ||            // overall optimize hash changed
  Object.keys(metadata.optimized).some(dep =>  // an already-loaded dep's bundle changed
    metadata.optimized[dep].fileHash !== newData.optimized[dep].fileHash);
```

So vite reloads when: the new dep is CJS (interop mismatch), re-bundling changes
the metadata hash (adding the first new dep does), or an already-loaded dep's
fileHash changes. A genuinely-new mid-session dep usually changes the hash, and
CJS always trips interop, so reload is largely unavoidable for new deps. The
no-reload path mainly holds when the dep was already optimized (i.e.
pre-bundled). `optimizeDeps.needsInterop` can pre-declare CJS deps to kill the
interop-mismatch reload, but the hash-change reload remains. True zero-reload
for arbitrary new deps means bypassing vite's optimizer (option 3).

## Current lean

(3) is the principled end-state and most squint-native (we already have the
eval + registry half, and esbuild). (1) is a fine interim that we have proven
works. (2) buys less than (3) for similar rework.

Decided to punt (see top): accept the reload on a never-seen dep, lean on
`optimizeDeps.include` for known deps. Revisit (3) later.

## Remaining browser-repl TODOs (beyond dep resolution)

The vite-HMR browser REPL shipped: `npm run dev` only, plugin (`vite.js`, as
`squint-cljs/vite`) owns compile + HMR + nREPL-over-WS, `:main` injects entries,
pr-str printing across console/nREPL/browser, JSX (`:jsx-runtime`) + reagami,
cross-ns live redefs. Still open:

- Promise printing as `#<Promise ...>` (needs boxing around the eval async-IIFE
  so a top-level promise isn't flattened by `await`).
- stdout streaming (`out`) + error/stacktrace formatting (`err` + ex-message)
  from the browser to the editor.
- interrupt / load-file over the browser transport.
- Multi-session: `!browser-send`/`!pending`/`last-ns`/`state` in the nREPL
  server are global (one editor/tab). Route by session; decide the multi-tab
  model.
- Dep follow-ups: auto-derive `optimizeDeps.include` from source `:require`s,
  or shadow-style on-demand bundling (options 1/3 above).
- Doc: CJS deps come back under `.default` (`(.. canvas-confetti -default)`).
- Doc: HMR self-accept re-runs module side effects (fine for re-render; matters
  for apps with listeners).
