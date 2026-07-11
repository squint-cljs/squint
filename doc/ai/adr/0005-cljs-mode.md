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
- Interning uses registry brands (the Symbol.for idea from the
  keywords-global branch, doc/dev/ideas.md) so two evaluated runtime
  copies still intern to the same instances.

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

## cljs+: name-keyed JS object access

Pure CLJS semantics would make `(get js-obj :foo)` return nil. This mode
deliberately diverges, keeping squint's interop ergonomics:

- Plain JS objects are name-keyed. `get` with a keyword reads
  `obj[name(k)]`, so keywords and strings both address the property.
  One `keyword?` check in the OBJECT_TYPE branch of `get`.
  `contains?`, `get-in`, `select-keys`, `update`, `assoc` and map
  destructuring bottom out there and work for free. Destructuring
  interop data directly, without `js->clj`, is the point.
- Persistent maps are value-keyed, real CLJS semantics.
  `(get {:a 1} "a")` is nil.
- The rule fits one sentence: object world is name-keyed, persistent
  world is value-keyed, the representation of the collection decides.

Why this does not reopen the altKey mess: in 0004 one representation
(objects) served both worlds, so every lookup was ambiguous and needed
double probing. Here the worlds are separate representations and
dispatch resolves the ambiguity before any lookup happens.

Documented edges, not solved ones:

- `(keys js-obj)` returns strings, because property names are strings.
  Comparisons on object-world keys are string comparisons.
- js/Map and js/Set stay identity-keyed, no coercion. Interned keyword
  keys work (same instance every time) but `(.get m "a")` does not find
  `:a`. Real CLJS behaves the same. Declining to coerce SameValueZero
  collections is what keeps altKey dead.

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
