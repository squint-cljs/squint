# ADR 0002: Compile-time namespaces - shipped scope and future work

Status: Proposed (future work; the shipped scope is Accepted)

## Context

`{:squint/compile-time true}` namespaces load only their compile-time part into
SCI: top-level defmacros squint's normal reader can see, plus
`^:squint/compile-time` marked forms, including marked forms in `#?(:clj ...)`
branches. See doc/compile-time.md for the user-facing feature.

The shipped mechanics, for reference by the extensions below:

- `squint.compiler.node/extraction-source` reads the flagged file with a
  `:read-cond` function (squint-normal branch resolution plus marked-`:clj`
  override), stashes each chosen branch's own source span
  (`:squint/inner-span`), and slices verbatim source text - so SCI's reader
  resolves syntax-quote and the extracted source contains no `:clj`
  conditionals. The SCI `:load-fn` serves this source for any flagged ns it
  resolves (`compile-time-source`); unflagged namespaces load whole (legacy).
- The target compile strips `^:squint/compile-time` forms (`transpile*` skips
  when the marker value is `true`) and emits no runtime var, export, or
  `:refer` import for macros.
- Requires of a flagged ns are carried into the extracted source as
  `:as-alias`, so aliased emitted refs (`str/join`) qualify without loading
  anything in SCI. A ref CALLED at expansion time must already be resident in
  SCI: a built-in (clojure.string), a shim (cljs.test, cljs.analyzer.api), or
  a same-ns marked helper.

Each extension below was prototyped and verified during the design of the
shipped scope, then deliberately deferred: fs (`with-temp-dir`) and
clojure-test-suite (`portability.cljc`) need none of them.

## Deferred extensions

### 1. Self-use in a flagged namespace

`(def x (my-macro ...))` in the flagged file itself does not expand: macro
loading is triggered by the CONSUMER's requires, and the file's own flag is
never consulted for its own compile. (The legacy self `:require-macros` clause
works and is the workaround.)

Mechanics (proven): in `scan-macros`, when the compiled file's own ns form is
flagged, add it to the macro requires with its compile-time source registered
directly from the in-memory source string (the file may not be resolvable on
`:paths`), and after harvesting wire ALL its macro names into the ns's own
`:refers` so unqualified calls expand. The two shadowing fixes this needs
(`excluded?` treating a `:macro` entry as non-shadowing, def-rename skipping
macros) shipped with the self `:require-macros` fix.

### 2. Helper namespaces called at expansion time

A macro calling a fn from another USER namespace at expansion fails
(`Unable to resolve symbol: h/build`): `:as-alias` registers the alias without
loading, and the ns is not a SCI built-in.

Mechanics (proven): a helper ns opts in with the same flag; the flagged ns's
extracted `:require` emits a real `[lib :as a]` for a required lib that is
itself flagged (unflagged stays `:as-alias`). Loading is lazy through the SCI
`:load-fn`, which already dispatches on the flag - transitive requires re-enter
it, already-loaded libs short-circuit, and cyclic loads throw
(`sci/impl/load.cljc`). No pre-walking, no visited set. Watch-mode staleness of
a transitively loaded helper matches the legacy transitive macro path.

### 3. `:ns` flag value - a whole compile-time namespace

`{:squint/compile-time :ns}` declares the entire namespace compile-time: the
load-fn serves the whole file, no runtime module is written, and a consumer
emits no import for it. The one-file replacement for a CLJS `.clj` macro
sibling (a real `.clj` sibling shadows the `.cljc` on the JVM, breaking
JVM/babashka consumers - rejected).

Mechanics (proven): `compile-time-source` returns the whole source for `:ns`;
`compile-file` skips writing when `source-flag` is `:ns`; `scan-macros` stashes
`:ns`-flagged required libs in ns-state (`:compile-time-nss`) and
`process-require-clause` emits no import for them (same pattern as the macro
`:refer` import suppression).

### 4. `:both` marker value - compile time AND runtime

A fn a macro CALLS at expansion for literals and EMITS calls to for dynamic
args has no expression: unmarked is runtime-only, marked is compile-time-only.
Prior art: C++ `consteval` (marked) vs `constexpr` (`:both`); macrovich's
deftime/usetime/outside-both zones; in CLJS self-require `.cljc` files plain
defns exist in both phases.

Mechanics (proven): extraction already keeps any truthy marker; the
`transpile*` strip already skips only marker value `true` (`(true? ...)`), so
a `:both` form is loaded AND emitted today - the extension is tests and
documentation.

## Considered and rejected

- Markerless "load the `:clj` view" (CLJS-faithful macro side): a corpus study
  of 176 macro-bearing `.cljc` files found 45% carry SCI-fatal JVM code in
  `:clj` branches and 86% carry top-level runtime forms; only 12% would work.
  Viable later as an opt-in flag value (e.g. `:clj`) for SCI-clean files.
- Auto-including a defmacro found in a `#?(:clj ...)` branch: the `:clj`
  branch is where JVM-only macro bodies live (e.g. timbre reads JVM config at
  expansion time for log elision); the explicit marker is the author's
  assertion that the body is SCI-safe.
- Marker on the def NAME (`^:private` style): one marker position keeps the
  rule simple.
- Marker carriers other than metadata (discard hint, wrapper form, tagged
  literal, reader feature): metadata is the only carrier attached to the form
  as data, runtime-invisible on every dialect, and requiring nothing defined.
  The `:squint/compile-time` READER FEATURE (0.14.198) was removed for the
  marker.
- A `foo.squint` platform-extension macro sibling (precedent: `.bb`, `.cljd`,
  `.cljr`): avoids the JVM shadowing of a `.clj` sibling, but breaks
  single-source and adds ecosystem friction; `:ns` namespaces cover the need.

## Consequences

The shipped scope covers the motivating libraries with one ns flag and, where
needed, per-form markers. The extensions are additive and independently
shippable in the order listed; each has working mechanics recorded here and
prototype-verified test shapes (expansion-helper namespaces with runtime-throw
canaries, `:ns` import suppression, `:both` literal folding).
