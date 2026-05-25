# Browser REPL: dependency resolution design

Design notes for how REPL-eval'd code should resolve its npm/cljs dependencies
in the browser, without forcing a page reload. Companion to
`browser-repl-todo.md`.

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

## Current lean

(3) is the principled end-state and most squint-native (we already have the
eval + registry half, and esbuild). (1) is a fine interim that we have proven
works. (2) buys less than (3) for similar rework.

Open questions to decide:

- Do we require brand-new deps at the REPL to "just work" (rules out 2)?
- Is one reload on a never-seen dep acceptable (then 1 is enough for a while)?
- For (3): where does the on-demand bundle get built and served, and how does
  eval'd code reference the registry instead of emitting `import()`?
