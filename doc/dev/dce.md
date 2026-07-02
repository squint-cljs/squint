# Tree-shaking / dead-code elimination of squint output

Notes on how well squint's JS output tree-shakes under a bundler (esbuild),
where it fails, and how to fix it. Benchmark: `examples/replicant`.

All byte counts below are `esbuild --bundle --minify --format=esm` output.

## Summary

1. `vite build` already emits tree-shakeable static imports. The committed
   `examples/replicant/js/*.js` is stale *dev* output and is NOT representative.
2. There is a fixed ~5.4KB floor that every squint import drags in, caused by
   (a) two top-level protocol-mutation statements and (b) collection classes
   with computed Symbol-key members that esbuild cannot eliminate.
3. Symbols can be kept and still get DCE by splitting the Symbol-keyed classes
   into their own ES modules, relying on package-level `sideEffects: false`.

## Static vs dynamic import

The compiler emits a static namespace import for normal compilation and a
dynamic `await import()` only in REPL mode (`compiler.cljc` `compile*`,
`repl?`). The vite plugin sets `repl: !isBuild` (`vite.js`), so:

- `vite build` -> `import * as squint_core from 'squint-cljs/core.js'` ->
  tree-shakeable.
- dev / REPL -> `var squint_core = await import('squint-cljs/core.js')` -> NOT
  tree-shakeable (whole module retained). Fine because dev output is not
  bundled for production.

Core-only cost by import style:

| import style                         | bytes  |
|--------------------------------------|--------|
| `await import()` (dev/REPL)          | 35,873 |
| `import * as squint_core` (build)    |  5,886 |
| named `{ atom, swap_BANG_, ... }`    |  5,887 |

esbuild tree-shakes static namespace import and named import equally; weaker
bundlers may do better with named imports.

The committed `examples/replicant/js/` is dynamic-import dev output. It makes
squint look non-tree-shakeable. Regenerate or gitignore it.

## The ~5.4KB floor

A no-op import drags in a large chain:

| import                                   | bytes |
|------------------------------------------|-------|
| `identity` (literally `return x`)        | 5,377 |
| `inc` / `str` / `deref`                  | ~5,38x |
| `atom`                                    | 5,752 |
| map,filter,reduce,assoc,get,conj,vec     | 8,800 |

Importing `identity` retains: `dequal`, `get`, `LazyIterable`, `Cons`, `List`,
`SortedSet`, `sort`, `compare`. `identity` references none of these. Two causes.

### Cause 1: top-level protocol mutations (~0.9KB)

Two top-level statements in `src/squint/core.js`:

```js
LazyIterable.prototype[IIterable] = true;   // ~line 907
concat.squint$lang$variadic = (colls) => { ... }; // the apply hint
```

These are side-effecting assignments that reference their target symbols, so
esbuild keeps them and transitively whatever they reference. Removing both
drops the `identity` floor 5,377 -> 4,509. `sideEffects: false` in
`package.json` does NOT shed them (verified via real package resolution).

### Cause 2: computed Symbol-key class members (~4.5KB)

esbuild will not dead-code-eliminate a class that has a computed member key
unless the key is a literal string/number. Confirmed:

```js
class C { [SYM](){} }          // unused -> KEPT  (SYM is a const Symbol)
class C { [Symbol("x")](){} }  // unused -> KEPT  (inline Symbol call)
class C { [Symbol.for("x")](){} }            // KEPT
class C { [/* @__PURE__ */ Symbol("x")](){} }// KEPT (annotation ignored here)
class C { [Symbol.iterator](){} }            // KEPT (even well-known symbol)
class C { ["squint$IIterable"](){} }         // unused -> DROPPED (literal!)
class C { plainMethod(){} }                  // unused -> DROPPED
```

A non-literal computed key marks the class definition as possibly
side-effecting (the key could be a getter), so esbuild retains the class. The
post-class form `C.prototype[SYM] = ...` does not help: the assignment
statement references `C` and keeps it.

squint's collection classes (`LazyIterable`, `Cons`, `List`, `SortedSet`, ...)
all carry protocol methods under computed Symbol keys (`[IIterable]`,
`[Symbol.iterator]`, ...). Each is therefore retained
unconditionally and drags in what its methods reference (`dequal`, `get`,
`sort`, `compare`). That is the floor.

## Implemented

The floor was removed (branch `dce-tree-shaking`). Everything stays in the
single `core.js`; no new public symbols, no new modules. Two tiny helpers,
marked `@__NO_SIDE_EFFECTS__` so the annotation lives at the definition (no
per-call `/* @__PURE__ */`):

- `defclass(c)` returns its class arg. Used as `const C = defclass(\nclass C {
  ... })` with the class body kept flush-left (no re-indent). A bare `class C {
  [Symbol.iterator](){} }` is retained unconditionally (computed key looks
  side-effecting); passing it through a no-side-effects call lets a bundler drop
  it when `C` is unused.
- `withApply(f, applyFn)` sets `f.squint$lang$variadic = applyFn` and returns
  `f`. Used as `export const concat = withApply(fn, applyFn)`. A bare top-level
  `concat.squint$lang$variadic = ...` is a side effect bundlers never drop;
  routing it through the no-side-effects helper lets concat drop when unused.
  Scales to any number of variadic-apply fns, no per-fn module.

`@__NO_SIDE_EFFECTS__` needs esbuild 0.18+ or rollup 3.13+ (vite 5-8 ship rollup
4, so vite dev=esbuild and prod=rollup both honor it). squint's own devDep
esbuild was bumped 0.14 -> 0.28. On a bundler that ignores the annotation, DCE
degrades silently (the floor returns); nothing breaks.

Three techniques remove the floor:

1. `LazyIterable`, `Cons` and `SortedSet` (the computed-`[Symbol.iterator]`
   classes) are each `defclass(class ...)`, so a bundler drops the ones an app
   never constructs.
2. Brand-based dispatch. `typeConst`, `dequal` and `isVectorArray` read an
   instance brand (`TYPE_TAG`, set in each constructor) instead of `instanceof
   List/LazyIterable/SortedSet`, so the central path that every app pulls does
   not reference the classes. The remaining `instanceof` checks live inside seq
   fns that legitimately use a class, so they only pull it when that fn is used.
3. `concat` uses `withApply` (above).

The public export surface is byte-for-byte identical to the previous release
(verified by diffing the `export` lines); the helpers and `TYPE_TAG` are
unexported module-locals.

The apply hint (`squint$lang$variadic`, set by `withApply` and by variadic/
multi-arity codegen) is a string property, not a symbol or export. See
doc/ai/adr/0001-variadic-fn-native-rest.md for the variadic/multi-arity codegen and
lazy apply that build on this.

Results, esbuild 0.28 minified, importing from `squint-cljs/core.js`:

| slice                                  | before | after | saved |
|----------------------------------------|--------|-------|-------|
| `identity`                             |  5,377 |    39 |  99%  |
| `atom,deref,reset!,swap!`              |  5,887 | 1,309 |  78%  |
| `map,filter,reduce,vec,range`          |  7,462 | 5,287 |  29%  |
| `str,println,pr-str`                   |  7,576 | 5,174 |  31%  |
| 14-fn realistic mix                    |  9,588 | 8,750 |   8%  |

The win scales with how much of core an app does NOT use. Apps that use lazy
seqs (`map`/`filter`/...) still pull `LazyIterable`, which is correct. The
`examples/replicant` production build is unchanged (~61KB): it uses seqs and is
dominated by the replicant library, not core. A `reagami` hello app (input +
button) drops ~5% gzip (5.83 -> 5.55 kB).

Verified in a real browser: an importmap that maps only `squint-cljs/core.js`
still works unchanged. `core.js` stays a single module with no new sibling
imports, so importmap users see no extra fetches and no breakage.

### Two non-obvious blockers, and why the wrappers work

- A computed (non-literal) class member key is never tree-shaken. `class C {
  [SYM](){} }` is retained even when unused, with any non-literal key (a const
  Symbol, `Symbol("x")`, `Symbol.for(...)`, even the well-known
  `Symbol.iterator`); the key could be a getter, so the definition is treated as
  side-effecting. A literal key (`["lit$key"]`) DOES drop. `defclass(() => class
  ...)` sidesteps this: the class expression is built inside a call, and the
  `/* @__PURE__ */` marks the call droppable.
- A top-level property mutation (`fn[SYM] = ...`) is never dropped, even with
  `sideEffects: false`. `withApply` defers the mutation into a `/* @__PURE__ */`
  call so it drops with the fn.

Both wrappers keep Symbols. Symbols are the right tool for collision-free
protocol dispatch; the problem was purely how bundlers treat computed keys and
top-level mutations, not Symbols at runtime.

## Benchmarks

- `examples/replicant` `npm run build`: ~61KB / 18.8KB gzip, unchanged. It uses
  seqs and is dominated by the replicant library, so core's slice is already
  small.
- `reagami` hello app (input + button): 5.83 -> 5.55 kB gzip (~5%). Seq-using.
- The win concentrates in small / non-seq apps, where the old ~5.4KB fixed floor
  was a large fraction of total size (now ~0.2KB for an atom-only app).

## Other opportunities (not done)

- Emit named imports of only the used vars instead of `import * as` for more
  robust tree-shaking on weaker bundlers (webpack, old rollup). esbuild is
  already equal.
