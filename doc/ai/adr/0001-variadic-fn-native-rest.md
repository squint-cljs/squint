# ADR 0001: Variadic fns use native rest params + a string apply hint

Status: Accepted

## Context

A variadic fn `(defn foo [x & xs] ...)` used to compile to a ~20 line IIFE: an
`arguments`-copy loop, a `cljs$core$IFn$_invoke$arity$variadic` impl, and
`cljs$lang$maxFixedArity`. The direct-call path was noisy and slow (touching
`arguments` deopts V8), and `apply` did not use any of it: `apply` spread the
collection (`f(...xs, ...coll)`), which crashes (`RangeError`) on large colls and
diverges on infinite/lazy ones (e.g. `(apply (fn [x & xs] x) (range))`).

We want:
- direct calls to use native JS rest params (small, fast, real Array rest);
- `apply` over a variadic fn to stay lazy and not spread (match CLJS, no crash).

JS constraint: at runtime you cannot tell a rest-param function from a fixed one
(`f.length` counts only params before the rest). So `apply` needs a hint on the
fn to know it is variadic and how to call it without spreading.

## Decision

Variadic fns compile to:

```js
var foo = /* @__PURE__ */ (() => {
  const impl = function (x, xs) { ... };                 // body; xs is a seq/array
  const foo = function (x, ...xs) {                      // facade: native rest
    return impl(x, xs.length === 0 ? null : xs);         // empty rest -> nil
  };
  foo["squint$lang$variadic"] = impl;                    // the apply hint
  return foo;
})();
```

- Direct calls hit the native-rest facade, which delegates to `impl`. No
  `arguments`, no loop. Self-contained: no dependency on core.js.
- `apply` reads `f.squint$lang$variadic`; if present it pulls the fixed args
  lazily (`first`/`next`, count from `impl.length - 1`) and passes the rest as an
  unrealized seq to `impl`. Otherwise it falls back to the spread path.
- `withApply` (used by `concat`) sets the same property, so core variadic fns and
  compiled variadic fns share one mechanism.

The hint is a plain string property, not a symbol or a core export.

### Multi-arity

Multi-arity fns follow the same shape. Each arity compiles to its own `impl`
fn (which does its own param destructuring); a facade switches on `args.length`
and passes RAW `args[i]` through to the matching impl. The variadic arity (if
any) uses a native rest slice and is stashed under `squint$lang$variadic`.

```js
var foo = /* @__PURE__ */ (() => {
  const impl1 = function (a) { ... };
  const impl2 = function (a, b) { ... };
  const implV = function (a, b, r) { ... };          // r is a seq
  const foo = function (...args) {
    switch (args.length) {
      case 1: return impl1(args[0]);
      case 2: return impl2(args[0], args[1]);
      default: { const rest = args.slice(2);
                 return implV(args[0], args[1], rest.length === 0 ? null : rest); }
    }
  };
  foo["squint$lang$variadic"] = implV;               // only if a variadic arity
  return foo;
})();
```

`apply` does a bounded pull of up to `maxfa = implV.length - 1` fixed args
(never realizing a lazy coll past them): if more remain it is a variadic call
(`implV(fixed..., restSeq)`, lazy); if not, the total args land on a fixed arity,
so it spreads into the facade which dispatches by count. This picks the right
fixed arity vs variadic the way CLJS's bounded-count does.

CRITICAL (both single- and multi-arity): the facade must pass RAW params to the
impl. Splicing the fixed params - which may be destructuring forms like
`{:keys [a b]}` - into the impl CALL emits them as map/vector literals, not
values (regression that broke replicant/clojure-mode: a destructured arg became
`{"keys":[a,b]}`). Only the impl destructures.

## Consequences

- `apply` over a variadic fn is lazy and never crashes on large/infinite colls
  (matches CLJS); only fixed args are realized.
- The hint is an optimization, not a hard ABI: `apply` always has the spread
  fallback, so a fn without the property still works for normal sizes. A future
  squint can change the mechanism and keep recognizing
  `squint$lang$variadic` for backward compat; old compiled code never hard-breaks.
- Smaller, faster direct calls (~native rest; no `arguments` deopt).
- No new public export on core.js (honors that rule); a string key is global, so
  it also works across duplicate `squint-cljs` instances in a dep tree.
- Collision risk: `apply` runs on arbitrary values, so a value carrying a
  `squint$lang$variadic` property would be treated as variadic. The `$`-namespaced
  name on function objects makes this negligible.

## Alternatives considered

- Exported `Symbol`: smallest minified key, but adds a public core export and is
  per-core-instance (breaks across duplicate cores).
- `Symbol.for('squint.lang.variadic')`: global + no export, but larger raw and a
  `Symbol.for()` call per fn; gzipped it is slightly larger than the string.
- Reusing CLJS `cljs$lang$applyTo`: different contract (CLJS `applyTo` takes the
  full arglist and splits internally; ours stores the impl and `apply` splits).
  Adopting the name without the contract is a false-compatibility footgun;
  adopting the contract needs a per-fn applyTo wrapper (bigger) for CLJS/cherry
  interop that squint cannot use (incompatible data model, no IFn, separate
  runtime).
- Native rest only, no hint: keeps the codegen win but loses lazy/safe `apply`
  (the pre-existing spread crash remains). Rejected because the hint is cheap and
  the lazy apply matches CLJS.
