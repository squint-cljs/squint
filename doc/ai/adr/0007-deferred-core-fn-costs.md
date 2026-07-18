# ADR 0007: Structural costs in the protocol and variadic core

Status: Accepted (protocol cost: keep baseline; variadic cost: deferred)

## Context

Two structural costs in the core runtime were measured this cycle. Each has a
known fix that is real work with real risk, and neither prize is large enough to
justify that work now. This ADR records the measurements and the decision to
defer, with the trigger that should reopen each.

### Protocol dispatch bundle cost

Protocol dispatch arrived in two waves, both pinning machinery into plain-data
bundles.

Wave 1, atoms (0.14.201): `IReset`/`ISwap`/`IWatchable`/`IAtom`/`IDeref` were
added to `atom`/`swap!`/`reset!`/`deref`/`add-watch`. An app using plain atoms
(`swap!`, `add-watch`, no custom `IAtom` type) pins the lot.

Wave 2, collections (0.14.203-205): `assoc` names `IAssociative__assoc`, `conj`
names `ICollection__conj`, `get` names `ILookup__lookup`, `=` names
`IEquiv__equiv`, plus `INSTANCE_TYPE`. A broad app uses these together and pins
the whole set. ADR 0006 and `symbol-pure-dce` shake out slots an app does not
use; the union a real app references survives.

Measured with the js-framework-benchmark reagami app, a clean official
`build-prod` per squint version (compiled and bundled entirely on each version,
gzipped):

| squint | gzip | note |
|---|---|---|
| 0.14.197 | 7517 | before both waves |
| 0.14.200 | 7705 | before atoms |
| 0.14.201 | 8644 | +939, atom protocols |
| 0.14.202 | 8648 | before collections |
| 0.14.205 | 9520 | collection protocols, unfixed |
| 0.14.206 | 9190 | collections, after symbol-pure-dce + plain-data (-330 vs 205) |

The bundle grew ~1.5 KB across those versions, but that number does NOT measure
recoverable protocol dispatch - it conflates three things: protocol slot
dispatch, non-slot additions (atom watch/validator expansion, `typeConst`,
`INSTANCE_TYPE`), and plain compiler-codegen evolution (0.14.197 -> 202 alone is
+1.1 KB with no protocol layer at all). Attributing the whole 1.5 KB to protocols
- as an earlier draft of this ADR did - is wrong.

What is actually recoverable was measured directly: transform the shipping
0.14.206 core.js in place, hold everything else constant, rebuild the reagami
app (see doc/ai/adr/0007-poc/measure-*.cjs):

| reagami app | gzip | recovers |
|---|---|---|
| baseline (as ships) | 9201 | - |
| protocol slot symbols removed, dispatch branches kept | 8894 | **307 B** |
| all protocol slot dispatch removed (branches + symbols) | 8301 | **900 B** |

So the *most* any protocol-dispatch refactor can recover is **900 bytes**, and
that ceiling needs the dispatch branches gone - only the perf-regressing wrapping
mechanism does that. A registry, which keeps the branches inline, recovers just
the symbols: **~307 bytes**. Not 1.5 KB. reagami and the demo define no
`deftype`/`defrecord`/`extend-type`, so this machinery is genuinely dead weight
for them - it is just far smaller than the version-delta suggested.

The fix inverts the dispatch, like CLJS: base fns handle native types
(Object/Array/primitive/Map/Set) inline and name no protocol slot; built-in
branded types (records, sorted collections, atoms) dispatch by brand; a
constructor registry, populated by `extend-type`/`deftype`/`defrecord`, is
consulted only for the remaining non-native types. An app with no protocol
impls pulls zero protocol machinery. A proof of concept (doc/ai/adr/0007-poc/)
validated this, ruled out one alternative on perf, and found the ABI blocker -
but the deciding fact turned out to be payoff: measured on the real app, the
registry recovers only ~307 bytes gzip (below), which is why the baseline wins.

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

## Proof of concept

Three standalone experiments in doc/ai/adr/0007-poc/ (run with `node`), modelling
`get`:

- perf.cjs - the plain-data hot path is flat (~1x baseline) for the protocol-free
  base. But the naive "wrap the fn" mechanism regresses 2.4x the moment any
  `extend-type` exists, because every call then threads the wrapper (and it
  compounds per extension). A registry the base consults only for non-native
  types stays flat in both cases.
- dce.cjs - with a base that names no slot, a plain-data app bundle drops every
  protocol symbol (12 -> 0 in the model) and shrinks. Conditional: built-in
  types must not be eager-registered (`registry.set(T, ...)` at top level is a
  side effect that pins T into every bundle), so they stay brand-dispatched.
- extend.cjs - a user can extend their own type to a built-in protocol (works via
  the registry). Overriding a native type is ignored (the inline path wins) -
  which is already true in today's squint, so no regression.

Decision matrix (++ good, -- deal-breaker, ~ caveat; squint down each column):

| criterion                    | baseline (slots) | protocol-free | wrapping | registry (hybrid) |
|------------------------------|:----------------:|:-------------:|:--------:|:-----------------:|
| hot path, plain data         |        ++        |      ++       |    ++    |        ++         |
| hot path, extends present    |        ++        |      ++       |  **--**  |        ++         |
| DCE: slot symbols shake out  |      **--**      |      ++       |    ++    |        ++         |
| runtime-extensible           |        ++        |    **--**     |    ++    |        ++         |
| backward-compatible ABI      |        ++        |    **--**     |  **--**  |      **--**       |
| built-in types DCE cleanly   |        ++        |      ~        |    ~     |         ~         |

Reading the columns: baseline fails only DCE; protocol-free and wrapping each
carry two deal-breakers; the registry hybrid carries exactly one - the ABI break,
shared by *every* option that recovers DCE. So among mechanisms that fix DCE,
registry is strictly best. But the matrix only ranks *mechanisms*; it does not
weigh the *payoff*, and the measurement above settles that separately: the DCE
win is ~307 bytes. A strictly-best mechanism for a 307-byte win behind a breaking
change still loses to doing nothing.

## The blocker: it is a breaking ABI change

The mechanism moves protocol dispatch from prototype slots to a registry, so it
changes the contract between compiled code and core.js. `extend-type`/`deftype`/
`defrecord` would emit registry calls instead of `prototype[SLOT] = fn`, and core
fns would consult the registry instead of `coll[SLOT]`. Consequences:

- Code compiled by an older squint (slot-style) run against a newer registry
  core.js has its protocol impls silently ignored, and vice versa. A pre-compiled
  published library that uses protocols breaks against a mismatched core.js.
- It cannot be made backward-compatible. Keeping a slot check in the base fns for
  old code re-references the slot symbol, which pins it, which forfeits the entire
  DCE win. DCE and slot-compat are mutually exclusive.

So this is a breaking change requiring recompilation of protocol-using code. Even
setting the small payoff aside, that alone would gate it to a deliberate window;
combined with the ~307-byte payoff, it is simply not worth doing. reagami
itself is unaffected (it defines no protocols), but the change is squint-wide.

## Decision

Keep the baseline for the protocol cost; defer the variadic one.

- Protocol dispatch: **do not do it.** The measurement is decisive - a registry
  recovers ~307 bytes gzip, and even the 900-byte ceiling needs the wrapping
  mechanism that regresses the hot path 2.4x per extend. Paying a breaking ABI
  change and a `deftype`/`defrecord`/`extend-type` recompile to recover ~300
  bytes is a bad trade. The mechanism was feasible and perf-neutral; the payoff
  simply is not there. The POC and matrix stand as the record of *why* the
  baseline wins.
- The variadic runtime patch was tried in #961 and dropped: a 2-arg `min`/`max`
  arity that node cannot show a win for and that Chrome does not benefit from. The
  real fix is the compiler-level per-arity emission, which is larger - deferred to
  the doc/ai/ideas.md fixed-arity work.

## Consequences

- `bb test:size` runs in CI (this ADR's companion change), so any *future* size
  regression is visible per commit and fails the build past the threshold.
- reagami sidesteps the `min` cost with `js/Math.min` and depends on neither fix.
  It stays current with squint and pays the small protocol overhead, still the
  smallest app in the benchmark table.
- The protocol design is documented and disproven-on-payoff, so it does not get
  re-litigated from scratch: the number to beat is ~307 bytes gzip.

## When to revisit

- Protocol bundle cost: only if the recoverable grows materially - a much larger
  protocol surface, or a use case where 300 bytes matters and a coordinated
  recompile is cheap. On today's numbers it is not worth reopening. `test:size`
  guards against the cost *growing* unnoticed.
- Variadic call cost: alongside the fixed-arity codegen work in doc/ai/ideas.md,
  which supersedes the reverted runtime patch.
