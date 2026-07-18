# ADR 0007: Deferred structural costs in the protocol and variadic core

Status: Accepted (deferral)

## Context

Two structural costs in the core runtime were measured this cycle. Each has a
known fix that is real work with real risk, and neither prize is large enough to
justify that work now. This ADR records the measurements and the decision to
defer, with the trigger that should reopen each.

### Protocol dispatch bundle cost

The protocol layer (0.14.203) made the common core fns reference their protocol
slot to dispatch to custom types: `assoc` names `IAssociative__assoc`, `conj`
names `ICollection__conj`, `get` names `ILookup__lookup`, `=` names
`IEquiv__equiv`, and so on. ADR 0006 and `symbol-pure-dce` shake out the slots an
app does not use. But a broad app uses `assoc` and `conj` and `count` and `get`
and `=` together, so the union of its fns references the whole slot set, plus
`INSTANCE_TYPE`. That block is then pinned into the bundle.

Measured on the js-framework-benchmark reagami app (same compiled app, only the
runtime core.js swapped):

| runtime | raw (min) | gzip |
|---|---|---|
| main | 25973 | 9197 |
| 0.14.202 (pre-protocol) | 24136 | 8659 |
| delta | +1837 | **+538** |

reagami and the demo define no `deftype`/`defrecord`/`extend-type`, so for them
the whole block is dead weight: it dispatches to custom impls that do not exist.
This is true of most plain-data apps.

The fix is to invert the dispatch, like CLJS: base fns handle only native types
(Object/Array/primitive/Map/Set) and name no protocol slot; `extend-type` and
`deftype`/`defrecord` install the dispatch. An app with no protocol impls pulls
zero protocol machinery. The DCE-clean mechanism is for `extend-type` to wrap the
affected core fns, so the base fn holds no protocol reference to retain. The
binding constraint is that ESM imports are read-only, so the fns need a mutable
holder or a registry indirection.

### Variadic call-site cost

`min`/`max`/`conj` and other variadic core fns take `(...xs)`, allocating a rest
array on every call including the common fixed-arity case. Evidence from the
0.14.199 `min` regression: a keyed vdom patch calls `min` once per element,
~7000x per render, and reagami regressed ~18% on the benchmark remove op.
Dispatching on `arguments.length` inside the rest-args fn does not help - the
check is free, but V8 elides the rest array only when inlining plus escape
analysis both succeed, and in throttled Chrome they did not (a fast-arity runtime
`min` measured as slow as plain rest-args; only the `Math.min` intrinsic restored
0.14.197 speed). So the fix has to be at the call site: emit a per-arity call
(CLJS `.cljs$core$IFn$_invoke$arity$2` style). #885 tried inline emission and
#887 reverted it (double-evaluation); per-arity functions avoid that. This is the
doc/ai/ideas.md "Emit direct fixed-arity calls" idea.

## Decision

Defer both. Ship neither fix now.

- The protocol bundle cost is ~1/2 KB gzip per broad plain-data app. Recovering
  it means reworking the protocol system that landed weeks ago, with regression
  risk to records, transients and `extend-type`. The trade does not clear the bar
  today.
- The variadic runtime patch was tried in #961 and dropped: a 2-arg `min`/`max`
  arity that node cannot show a win for and that Chrome does not benefit from. The
  real fix is the compiler-level per-arity emission, which is larger.

## Consequences

- `bb test:size` runs in CI (this ADR's companion change), so the protocol cost
  is visible per commit and a regression past the threshold fails the build.
- reagami sidesteps the `min` cost with `js/Math.min` and does not depend on
  either fix. It stays current with squint and pays the ~1/2 KB, still the
  smallest app in the benchmark table.
- Both fixes remain open, documented with measurements, ready to execute.

## When to revisit

- Protocol bundle cost: when `test:size` shows the tax crossing a threshold that
  matters, or a size-sensitive consumer is blocked by it - by then the protocol
  code is battle-tested and the inversion is far less risky than doing it now
  against fresh code.
- Variadic call cost: alongside the fixed-arity codegen work in doc/ai/ideas.md,
  which supersedes the reverted runtime patch.
