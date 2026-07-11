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

A global compiler flag. One mode per app, applied to the app and all
dependencies, since squint compiles dependencies from source.
Per-namespace mixing is out: data crosses namespaces and the dialects
disagree on lookup and equality.

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

- A second dialect for library authors. The mode is viral per app, so a
  squint library either targets one dialect or tests both. Everything
  published today assumes keywords are strings and maps are objects.
- The 0004 interop traps (typeof gates, structuredClone, `===` in JS
  libraries) return as documented mode semantics. Acceptable when the
  app opted in, deadly when ambient. That is the line between this
  proposal and the rejected default.
- The overlay module, the literal emitters and a doubled portion of the
  test suite.

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
- Reader conditional support for dual-target libraries, for example a
  `:squint/cljs` feature. A lib's existing `:squint` branch assumes
  strings and objects, so in cljs mode it is the wrong branch to take.
  The mode's feature list could be `[:squint/cljs :cljs :default]`,
  skipping bare `:squint`: under real keywords and persistent
  collections a lib's `:cljs` branch is usually the more correct code,
  so untouched CLJS libraries might compile as-is. Counterweight: `:cljs`
  branches can lean on hosts squint lacks (goog, cljs.core internals).
  Needs more thought.
