# ADR 0006: imm macro for immutable collection literals

Status: Accepted, branch `immutable` (July 2026)

## Context

ADR 0004 chose the refer pattern for opting into `squint.immutable` per
namespace. That covers the fn API but not literals, and the array-map
tier (0004 follow-up list) needs a literal creation point: in CLJS the
tiering lives in map literals, `hash-map` always builds a hash map.
Rulings: `hash-map` returning an array map is rejected, `(hash-map)`
stays an empty hash map, an `array-map` fn is fine, promotion at 8 is
correct. So squint needs a literal variant.

Alternatives considered:

1. Reader tag `#imm {:a 1}`. Precedent `#js`, but squint-only syntax:
   CLJS/JVM need a data_readers dependency to read it at all.
2. Metadata hint `^imm {:a 1}` or `^:imm {:a 1}`. Reads everywhere, but
   measured on both platforms (JVM `clojure`, planck): literal metadata
   is evaluated, so `^imm` needs a `(def imm nil)` anyway, and the meta
   survives to runtime (`{:tag nil}` resp. `{:imm true}`). Same ceremony
   as a macro override plus residue plus squatting the type-hint slot.
   Rejected.
3. Runtime conversion fn `(->imm x)`. Lossy for literals: JS objects
   stringify keys before any function runs, `{1 2}` and `{[1 2] 3}` are
   unrecoverable. Only useful as a separate converter for dynamic
   string-keyed data (future work, js->clj analog).
4. Core macro `imm`. Compile-time deep walk over the literal form, so
   key types are preserved. Chosen.

## Decision

`(imm coll-literal)` is a built-in core macro. It requires a map, vector
or set literal (compile error otherwise) and deep-converts nested
collection literals to `squint.immutable` constructor calls. Expressions
inside the literal are inserted as values, shallow #js-style conversion
of only the outer literal was rejected as the classic footgun.

- Emission: `squint_imm.hash_map/vector/hash_set` calls, with an
  auto-injected `import * as squint_imm from
  'squint-cljs/src/squint/immutable.js'` via a `:need-imm-import` env
  atom, same mechanism as defrecord's record.js import.
- No require needed in squint. For clj-kondo,
  `#?(:squint (:require [squint.core :refer [imm]]))` satisfies the
  linter and is a no-op in the compiler.
- CLJS/JVM override: `#?(:cljs (def imm identity))` (literals there are
  already persistent). The require must stay reader-conditional since
  squint.core is not a loadable namespace on those platforms.
- The js* expansion carries no `object` tag, so `get`/`assoc` inlining
  stays on the runtime path.
- equiv keying applies: a plain `[1 2]` never finds an `(imm [1 2])`
  key, lookups use `(imm ...)` keys too.

## Future

- Map emission switches to array-map tiering when the PAM tier lands
  (0004 follow-up), `(imm {})` becomes the empty array map per CLJS
  literal semantics.
- Optional `#imm {...}` reader sugar expanding to the macro call, plus a
  published data_readers.cljc mapping `imm` to identity for .cljc
  sources.
- Optional runtime deep converter for dynamic data.
