# Browser REPL generic mode: status

Session state as of 2026-07-27. Companion design doc:
`browser-repl-webpack-design.md` (same dir, also published as a claude.ai
artifact, keep both in sync).

## Branches

- `browser-repl-on-demand-deps` (commit 575d558f): vite plugin bundles a
  never-seen npm dep on demand with esbuild and serves it at `/@repl-deps/`,
  a route vite never analyzes, so the optimizer never reloads the page.
  Key trap encoded in comments: never `container.resolveId` an unknown bare
  package spec (it registers a missing dep and schedules the reload), and
  bare specs WITH a .js extension must keep going through vite's resolver
  (a second core.js instance breaks atom identity).
- `browser-repl-webpack-plugin` (on top of the above, commits 7279fbc8,
  22824256, 528393e7, 73a3573c, plus this doc): `SquintPlugin` webpack
  adapter for the generic mode. New code in `src/squint/repl/`: `client.js`
  (browser client, `squint-cljs/repl-client`), `webpack.js`
  (`squint-cljs/webpack`), `deps.js` (shared esbuild bundler, also used by
  vite-common.js). Example in `examples/webpack-repl`, e2e in
  `e2e/webpack_repl_test.cljs`, wired into `bb test:e2e`.

Recommendation: two PRs in that order. The vite half is self-contained and
verified, the webpack half freezes new public API on merge.

## Verified

- `bb test:e2e --vite 5 --vite 6 --vite 7 --vite 8`: 142 PASS, 0 FAIL
  (node nREPL suite, vite browser suite per version, webpack suite). The
  vite 5 sweep proves the on-disk `_metadata.json` fallback (no live
  optimizer API before vite 6).
- Webpack suite covers: eval roundtrip, preact instance sharing via the
  manifest registry (asserted against the repl-mode refer binding
  `globalThis.app.render`, no app scaffolding), lodash via tier-3 blob over
  WS with no reload, clean `node:fs` error, `#jsx` at the REPL through the
  page's jsx runtime, `:hot :ws` state-preserving reload.
- jsx gap found and fixed: the compiler injects jsx runtime imports the
  manifest source scan cannot see, so `writeManifest` registers both
  `<import-source>/jsx-runtime` and `/jsx-dev-runtime`. General lesson:
  every compiler-injected require needs explicit manifest registration.
- WS eval socket binds 127.0.0.1 and refuses non-localhost `Origin`
  (cross-site WS hijacking).
- e2e ports (5399/1343/1344) are distinct from the example defaults
  (5299/1341/1342) via `SQUINT_NREPL_PORT` / `SQUINT_WS_PORT` env overrides
  in the plugin, so a running `npm run dev` in the example never collides.
  The e2e snapshots and restores the example's `js/repl_deps.js`.

## Before merging PR 1 (on-demand deps)

- CHANGELOG line (borkdude).
- Update the punt decision in `browser-repl-dep-resolution.md` (borkdude).
- Add esbuild to `peerDependenciesMeta` as optional. It is a devDependency
  today, vite projects get it hoisted via vite (breaks under pnpm, gone in
  rolldown-vite), the lazy import already errors with an install hint.

## Before merging PR 2 (webpack plugin)

Open API decisions, frozen on merge:

- Export names `squint-cljs/webpack` and `squint-cljs/repl-client`.
- Plugin option names (`nreplPort`, `wsPort`, `paths`, `outDir`, ...).
- The page registry global `__squint_deps`.
- The WS message protocol (`eval` / `complete-js` / `dep` / `load`), which a
  future bb/JVM server host would also speak. Documented at the top of
  `client.js`.

Known gaps, fine if documented:

- Only `:hot :ws`. The bundler-HMR mode (self-accept footer guarded for
  `import.meta.hot` / `import.meta.webpackHot`, see the design doc) is not
  implemented. The example sets `devServer hot:false liveReload:false`,
  required for `:hot :ws`.
- No user-facing docs, `doc/browser-repl.md` is vite-only.
- `ws` and esbuild not declared in `peerDependenciesMeta`.
- Tier-3 transitive duplication unmitigated in webpack mode (design doc has
  the registry-shim externalization plan). Stateful libs belong in the
  manifest.
- Manifest dep list is a regex source scan (`REQUIRE_RE` in webpack.js): the
  node API does not expose the string requires from ns-state. A compiler API
  for this would serve the manifest, `optimizeDeps.include` auto-derivation
  for vite, and any future host.
- Repl-mode output needs `experiments.topLevelAwait` in webpack config.
- `ws` CJS interop: `ws.WebSocketServer ?? ws.default.Server`.
- Multi-session/multi-tab routing is a shared TODO with the vite plugin.

## Future work (design doc has the details)

- bb/JVM server host: same wire protocol, http-kit WS, fswatcher pod,
  esbuild CLI or esm.sh for tier 3. Sequence after the protocol settles.
- `bundle-dep` as an injected capability (rolldown-vite future).
- Auto-derive `optimizeDeps.include` from source requires for the vite
  plugin, so tier 3 rarely fires there.
- stencil-cli themes are the "webpack as bare watch build" case: plugin in
  the theme's webpack.dev.js, `:hot :ws`, and the theme's own
  `process.send('reload')` in stencil.conf.cjs must be made conditional.

## Running things

- Full matrix: `bb test:e2e --vite 5 --vite 6 --vite 7 --vite 8`.
- Webpack suite alone: `node e2e/webpack_repl_test.mjs` from the repo root
  (mjs compiled by the e2e task).
- Example: `cd examples/webpack-repl && npm install && npm run dev`, page on
  :5299, nREPL on :1341.
- The e2e vite sweep npm-installs each version into examples/browser-repl,
  leaving package.json on the last version swept.
