# Squint dev notes

## Build / watch

- User keeps `bb dev` running (shadow-cljs watch on the `squint` build,
  `:output-dir "lib"`). It recompiles `lib/` on every save of a `.cljc`/`.cljs`
  source. Don't run `bb build` (full release): it does `fs/delete-tree "lib"`
  then a shadow release, which can leave `lib/` missing and break `bb dev`
  (runs `node_cli.js`, needs `lib/cli.js`).
- `bb dev` is a never-ending watch task. It prints `Build completed` once after
  startup (~20s) then keeps watching. If you start it yourself, kill it after
  that line.
- Verify compiler changes by grepping the emitted `lib/cljs-runtime/*.js` chunk
  (e.g. `lib/cljs-runtime/squint.compiler.js`) for the new code.

## Emission

- Emit each form once. Never emit a form as a probe and then emit it again,
  not even on a fallback path. Re-emission repeats side effects, advances
  gensyms and doubles per nesting level: an `and` chain of n operands ending in
  `recur` emitted the `recur` 2^n times under a probe.
- Decide from the form, the env or the context before emitting, or reuse the
  emitted text.

## Adding a core stdlib fn

Needs THREE steps (e.g. `dedupe`):

1. `export function` in `src/squint/core.js`.
2. Add the munged name (e.g. `dedupe`, `foo_QMARK_`) to
   `resources/squint/core.edn`. This list is the set of vars the compiler
   prefixes with `squint_core.`. A var NOT in core.edn emits as a BARE
   identifier (no prefix) -> runtime `ReferenceError`, with no compile-time
   warning (no-warn-on-unresolved is by design for now).
3. Recompile `lib/`. `core.edn` is inlined at compile time by the
   `edn-resource` macro (`slurp` at macroexpand, see
   `resources/squint/resource.clj`), so editing the edn alone does nothing
   until the shadow watch rebuilds `lib/cljs-runtime/squint.compiler.js`.

Transducer-capable fns follow the `arguments.length === 0 -> return xform`
pattern (see `filter`/`distinct` and their `*1` helpers). Use `_EQ_` for value
equality (deep), not `===`.

## Data representation

- Keywords are plain JS strings: `:foo/bar` -> `"foo/bar"`. No distinct keyword
  type, so `keyword?`/`symbol?`/`namespace` are absent by design. `name` exists.
- Maps are JS objects; keys are strings. Boolean/number map keys get
  stringified (lossy): `(get m false)` != `(get m "false")`.
