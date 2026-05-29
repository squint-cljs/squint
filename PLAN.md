# PLAN: kill the compiler dynvar mess

Goal: remove the global `^:dynamic` vars in `src/squint/compiler_common.cljc` (and
`*jsx*` in `compiler.cljc`). Thread per-compile config and lexical context through
`env`; fold per-namespace state into the existing `:ns-state` atom carried in `env`.

End state: no global mutable atoms as compiler state, no `*cljs-ns*` <-> `:ns-state
:current` sync patch, concurrent compiles safe.

This file is temporary scaffolding. Delete it when all bullets are done.

## Background

- `env` already carries `:ns-state` (an atom): `{:current ns-sym, ns-sym {:aliases
  .. :refers .. :vars ..}, :macros ..}`. See `compiler_common.cljc:402`,
  `compiler.cljc:233`.
- Entry point `compile*` (`compiler.cljc:455`) binds all dynvars from `opts`, then
  calls `transpile*`.
- The dynvars split into three kinds:
  - **lexical context** (rebound during a subtree): `*recur-targets*`, `*async*`
  - **per-compile config** (set once at entry): `*repl*`, `*target*`,
    `*core-package*`
  - **per-namespace state** (atoms, belong in `:ns-state`): `*aliases*`,
    `*imported-vars*`, `*excluded-core-vars*`, `*public-vars*`
  - **special**: `*cljs-ns*` (duplicates `:ns-state :current`), `*jsx*` (out-param:
    `set!` at `compiler.cljc:400`, read at `:509`)

## Rules of engagement

- One bullet per commit. Each bullet must compile and keep tests green on its own.
- After each bullet: `bb test:node` and `bb test:clj` (compiler is `.cljc`, runs
  both under Clojure/shadow and self-hosted). Add `bb test:libs` before the final
  bullet.
- Do not change emitted JS. These are pure refactors; output must be byte-identical.
  Spot-check by compiling an example before/after.
- Keep the dynvar `def` until its last reader is gone, then delete the `def` in the
  same commit that removes the last use.

## Bullets (attack in this order)

### 1. `*recur-targets*` -> `env` key  (warmup, lexical, 5 uses)
- Defs: `compiler_common.cljc:16`. Rebound at `:580`, `:1094`.
- Replace `(binding [*recur-targets* x] ..)` with `(assoc env :recur-targets x)`
  and read `(:recur-targets env)`. Thread `env` into the readers.
- Delete the `def`.

### 2. `*async*` -> `env` key  (lexical, 14 uses)
- Def `:12`. Rebound `:1173`, `:1479`; also set at entry from `(:async opts)`.
- Becomes `(:async env)`; entry sets `:async` in the initial env instead of binding.

### 3. `*target*` -> `env` key  (config, 14 uses)
- Def `:19`. Set at entry `compiler.cljc:468`/`:491`. Read across both files.
- Move to `(:target env)`, default `:squint` in the initial env map.

### 4. `*core-package*` -> `env` key  (config, 6 uses)
- Def `:306`. Set at entry `compiler.cljc:466`. Move to `(:core-package env)`.

### 5. `*repl*` -> `env` key  (config, 34 uses, mechanical but large)
- Def `:17`. Set at entry from `(:repl opts)`. Read widely incl. `compiler.cljc:368`.
- Move to `(:repl env)`. Largest blast radius; do alone, verify carefully.

### 6. `*imported-vars*` -> `:ns-state` (atom -> ns-keyed)  (9 uses)
- Def `:13`, created at `compiler.cljc:485`. Fold into `:ns-state` under current ns
  (or keep as an `opts`-carried atom if not per-ns; decide when there).

### 7. `*excluded-core-vars*` -> `:ns-state`  (5 uses)
- Def `:14`. Per-ns (`:exclude` from `ns` form). Fold into `[current :excludes]`.

### 8. `*aliases*` -> `:ns-state` (already dual-tracked!)  (8 uses)
- Def `:11`. ns-state ALREADY stores `[current :aliases]` (`:467`). The global atom
  is redundant/risky. Make all reads go through `:ns-state`, drop the global.

### 9. `*public-vars*` -> `:ns-state`  (6 uses, has explicit TODO at `:679`)
- Def `:15`. `def` emit already swaps ns-state. Fold `*public-vars*` reads into
  `[current :vars]`; remove the TODO comment.

### 10. `*cljs-ns*` -> `:ns-state :current`  (FINALE, 34 uses, removes the patch)
- Def `:18`. Every `(munge *cljs-ns*)` etc. becomes `(munge (:current @(:ns-state
  env)))` (or a small helper `(current-ns env)`).
- Delete the sync block at `compiler.cljc:494-499` ("Sync the ns-state's :current
  ... A leading (ns ..) form still updates it"). This is the patch we're killing.

### 11. `*jsx*` out-param -> atom in `opts`  (special, optional)
- Def `compiler.cljc:388`, `set!` at `:400`, read at `:509`. It's a write-up signal
  from deep emit to the entry. Convert to `(:jsx? opts)` as an atom; `set!` becomes
  `(reset! (:jsx? opts) true)`; read becomes `@(:jsx? opts)`.

## Done criteria

- `grep -n '\^:dynamic' src/squint/compiler_common.cljc src/squint/compiler.cljc`
  returns nothing (compiler dynvars). (`*debug*` in `internal/defmacro.clj` and the
  test-runner vars are out of scope.)
- All four test tasks green.
- This PLAN.md deleted.
