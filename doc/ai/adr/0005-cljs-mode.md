# ADR 0005: cljs mode, an opt-in dialect with real keywords and persistent collections

Status: Proposed. Design sketch, nothing implemented. Depends on the
immutable branch (persistent data structures with protocol-dispatched
core) being merged first.

## Context

ADR 0004 rejected real keywords as the default runtime representation.
The costs were ambient and the killer constraint was backward
compatibility: keywords had to keep working against string-keyed data,
which produced the altKey double lookup, the loose `=` debates and the
js/Map miss family. The benefit, wire-boundary type fidelity, turned out
to be solvable in userland at one encode/decode choke point (0004,
Resolution section).

The immutable branch changes the base assumptions. Once persistent
collections land in core and core functions dispatch through protocols,
core is representation-agnostic. A keyword type then no longer needs
core rewritten, it needs protocol implementations plus a small set of
overridden vars.

This ADR proposes an opt-in compile mode instead of a new default. The
mode owes no compatibility to string-keyed squint data, which removes
the constraint that generated most of 0004's pain.

## Decision (proposed)

A dialect resolved per namespace. `:squint/dialect` ns metadata pins a
namespace and travels with the source, so a library compiled from a git
dep or a jar keeps its dialect inside a consumer running the other one.
Ns metadata is the only channel that survives every packaging: squint
compiles dependencies from source directories and jars, where
out-of-band config like the library's own squint.edn is not present.

Initially ns pinning is the only mechanism: every namespace without
metadata is `:squint`, project and dependency alike. Explicit per file,
no inheritance rules to misread, and everything published before the
mode keeps the semantics it was written against. Inheriting the
consumer's mode would hand old libraries new semantics silently, the
ambient surprise 0004 rejected, moved to the dependency boundary.

Possible later conveniences, in rough order:

- Project-owned namespaces inherit the project's squint.edn `:dialect`,
  so an app does not annotate its own files. Dependency resolution
  already knows which source root a file came from.
- `:squint/dialect :inherit` for genuinely dual-dialect libraries,
  following the consumer on purpose instead of by omission.
- A consumer override per dependency or namespace as an escape hatch
  for legacy libraries, not ordinary behavior.
- A check that warns when a library's own squint.edn declares a dialect
  some namespace does not pin, so published libraries stay fully
  annotated.

Dialects therefore mix inside one program. The protocol-dispatched
runtime is shared, but a keyword literal is a string in one dialect and
a Keyword in the other, so lookups across the boundary miss. Data
crossing between dialects is the user's problem.

In cljs mode:

- Keyword literals compile to globally interned Keyword objects, hoisted
  as module-level constants. `case` keeps compiling to switch, since
  interning makes `===` correct. Symbols get the same treatment.
- Map, vector and set literals compile to persistent constructors.
- `(= :a "a")` is false. No name-equivalent equality anywhere.
- Keywords intern in a global table reachable through the Symbol.for
  registry, and protocol brands use registry symbols too (the idea in
  doc/dev/ideas.md). Two evaluated runtime copies hand out the same
  instances, so `===` and `case` work across copies. Weak references
  keep the table from pinning keywords, as in the POC's weak interning.

## Core function reuse

Most of core is reused unchanged through protocol dispatch. The overlay
module (working name `squint.cljs-mode`) overrides a short list:

- `keyword`, `keyword?`, `name`, `namespace`, `symbol`, `symbol?`,
  `find-keyword`
- printing (`pr-str` path for Keyword and Symbol)
- `clj->js` and `js->clj`, the sanctioned deep conversion
- the edn reader keyword hook

Remapping mechanism: a second edn resource next to core.edn listing the
overridden munged names. In cljs mode the compiler prefixes those vars
with the overlay import and everything else stays `squint_core`. Static
imports keep tree-shaking intact. Note core.edn is inlined at
macroexpand by `edn-resource`, the second table gets the same treatment
and the same rebuild footgun.

## cljs+: string access on JS objects

Pure CLJS `get` ignores JS objects entirely. This mode keeps squint's
string access and drops only the keyword side:

- `(get js-obj "foo")` reads the property, unlike CLJS. `assoc`,
  `contains?`, `get-in`, `select-keys` and `update` with string keys
  work through the same OBJECT_TYPE branch. `{:strs [a b]}`
  destructuring is the interop idiom.
- `(get js-obj :foo)` is nil, like CLJS. Keywords never coerce to
  property names. Crossing is explicit: `(name k)`, `js->clj`,
  `clj->js`. A keyword miss doubles as a signal that the value is JS
  data, not clj data.
- Persistent maps are value-keyed, real CLJS semantics.
  `(get {:a 1} "a")` is nil.
- The object world is purely string-keyed: `(keys js-obj)` returns
  strings and `(get o (first (keys o)))` works, fully symmetric.
- The rule fits one sentence: JS objects are string-keyed maps, keywords
  only key clj data.

Why this does not reopen the altKey mess: in 0004 one representation
(objects) served both worlds, so every lookup was ambiguous and needed
double probing. Here nothing coerces at all, each key type addresses the
collection kinds it natively fits.

Documented edges, not solved ones:

- js/Map and js/Set stay identity-keyed. Interned keyword keys work
  (same instance every time) but `(.get m "a")` does not find `:a`.
  Real CLJS behaves the same.
- Migration from default squint: `(:foo js-obj)` works today because
  keywords are strings, and under the mode it silently returns nil
  instead of erroring. The biggest porting trap in the design.

## What this costs

- A second dialect for library authors. A squint library either declares
  one dialect or tests both. Everything published today assumes keywords
  are strings and maps are objects, and predates the declaration, so it
  inherits whatever the consumer runs.
- The 0004 interop traps (typeof gates, structuredClone, `===` in JS
  libraries) return as documented mode semantics. Acceptable when the
  app opted in, deadly when ambient. That is the line between this
  proposal and the rejected default.
- The overlay module, the literal emitters and a doubled portion of the
  test suite.

## Dual-dialect libraries

Reader conditionals: the cljs mode feature set is
`[:squint/cljs :cljs :default]`, bare `:squint` deliberately absent.
Default squint already falls back to `:cljs`, so existing
`#?(:squint ... :cljs ...)` code does the right thing in both dialects
with no duplication: the `:cljs` branch serves real CLJS and cljs mode,
which share semantics by construction. `:squint/cljs` is the rare escape
key for the places where the mode diverges from real CLJS, such as
string access on JS objects, or `:cljs` branches leaning on hosts squint
lacks (goog, cljs.core internals).

A library that requires one dialect pins its namespaces and keeps it in
every consumer. A library with no metadata stays `:squint`, which is
what its code assumed when it was written. A library that works in both
dialects ships one source with those conditionals, initially compiling
as `:squint` like any unpinned code, later following the consumer via
`:squint/dialect :inherit`. Until then its data enters a cljs mode app
as string keywords, the ordinary cross-dialect boundary.

## Relationship to ADR 0004

Supersedes the "revisit if" framing for the opt-in case. 0004 remains
the measured record: the performance numbers, the boxed String method
trap and the typeof gate trap all apply to any code path where a
Keyword object crosses into JS unlowered, mode or not. What changes is
consent: cljs mode makes those documented dialect semantics instead of
ambient surprises.

## Open questions

- Whether symbols intern globally like keywords or stay per-call.
- Whether the immutable branch makes persistent literals available in
  default mode too, or only under the flag.
- Metadata on interned keywords (CLJS does not support it either).
- REPL and playground story when two dialects share one session.
- Shape of the consumer override escape hatch, per dependency or per
  namespace, for legacy libraries that predate the metadata.
