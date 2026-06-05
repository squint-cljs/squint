# Compiler env and ns-state

Reference for the squint compiler's `env` map and the `ns-state` atom it carries.
These hold the only compile-time knowledge of what namespaces, vars, aliases and
refers exist. squint emits plain JS and has no runtime namespace environment
(no `ns-resolve`, `ns-publics`, `all-ns`), so any feature that needs to know
"what is defined" (nREPL info/eldoc/complete, warnings, exports) must read
`ns-state`.

## env

`env` is the map threaded through every `emit` call. It is plain data plus a few
atoms. Relevant keys:

- `:ns-state` - an atom holding the namespace map (see below). The single source
  of truth for declared vars/aliases/refers across the whole compile.
- `:context` - `:statement` | `:expr` | `:return` | `:repl-return`.
- `:repl` - true when compiling for the REPL (emits `globalThis.<ns>.<var> = ...`
  assignments so evals are visible across forms).
- `:imports` - an atom (string) accumulating emitted import/await-import lines.
- `:aliases` - a static alias->ns map merged in by the caller (distinct from the
  per-ns `:aliases` recorded inside `ns-state`; resolution checks both).
- `:resolve-ns` - optional fn `(symbol-ns -> libname-string)` for auto-imports.
- `:var->ident`, `:quote`, `:jsx`, `:gensym`, `:core-alias`, `:top-level` - misc
  emit context.

`current-ns` (compiler_common.cljc:11) = `(or (:current @(:ns-state env)) 'user)`.

## ns-state atom

`(:ns-state env)` derefs to a single map. One special key plus one entry per
namespace:

```clojure
{:current my.app                ; symbol, the ns being compiled right now
 :jsx     true                  ; set if any JSX was emitted (drives jsx import)

 my.app  {:vars    #{"foo" "bar_QMARK_"}      ; MUNGED name strings (see note)
          :aliases  {str "squint-cljs/src/squint/string.js"  ; alias-sym -> RESOLVED libname
                     clojure_DOT_string "squint-cljs/src/squint/string.js"}
          :refers   {join clojure.string}      ; referred-sym -> original libname
          :excludes #{map}                     ; :refer-clojure :exclude set
          foo      {:doc "..." :line 3}        ; per-var meta map, key = UNMUNGED sym
          bar?     {}}

 clojure.string { ... }         ; other namespaces seen during this compile
 }
```

Notes / gotchas:

- Two parallel records of a def exist:
  1. `[ns :vars]` - a set of **munged name strings** (`foo_QMARK_`), used for
     `export { ... }` generation (compiler.cljc:526).
  2. `[ns <var-sym>]` - keyed by the **unmunged symbol**, value is a metadata
     map: `(select-keys (meta name) [:arglists :doc :line :file :private :macro])`
     (compiler_common.cljc:669-678). `defn` records `:arglists` and `:doc` into
     the name's meta (fn.cljc:319-344). So `(get-in @ns-state [ns sym])` yields
     e.g. `{:arglists ([a b]) :doc "..."}` - this is what nREPL info/eldoc read.
     A plain `(def x 1)` with no metadata stores `{}`.
- To enumerate a namespace's declared vars without munging, take the symbol keys
  of `(get @ns-state ns)` (everything that is a symbol, not the `:vars`/`:aliases`
  /`:refers`/`:excludes` keywords).
- `:aliases` values are the **resolved** libname (often a JS path like
  `"squint-cljs/src/squint/string.js"`, not `"clojure.string"`); can be a string
  or symbol. For a symbol require like `(:require my.macros)`, an alias-munged
  self-alias is also added (compiler_common.cljc:857-860). `:refers` values keep
  the original libname symbol.
- ns-state is mutated by `swap!` from many emit sites: `def`
  (compiler_common.cljc:672), `defclass*` (1440), `ns`/`:require`
  (876, 459, 793, 849), `:refer-clojure :exclude` (890).

## Where ns-state is created and seeded

- `compile*` creates a fresh `(atom {})` if the caller did not pass one
  (compiler.cljc:472) and seeds `:current` with `(:ns opts 'user)` before the
  first form (compiler.cljc:486). A leading `(ns ...)` form then updates
  `:current` (compiler_common.cljc:880).
- `compile-string*` returns the full state map (the merged `opts`), including
  `:javascript`, `:body`, `:imports`, `:exports`, `:ns` (the final current ns),
  and `:ns-state` (the same atom). The nREPL server threads this back into the
  next compile via the `state` arg so ns-state persists across evals
  (see `src/squint/repl/nrepl_server.cljs`, `state` / `!ns-state` atoms).

## REPL persistence

The nREPL server keeps two atoms (nrepl_server.cljs):

- `state` - the whole result of the last `compile-string*`; its `:ns-state` is
  threaded into the next compile so vars/aliases survive across evals.
- `!ns-state` - an optionally host-injected ns-state atom (e.g. the vite plugin
  shares the same atom it threads through file compiles), so REPL evals see vars
  that file compiles defined.

`current-ns` in the server reads `(:current @ (:ns-state of last state))`, falling
back to the host atom, then `'user`.

## Implication for info/eldoc/complete (implemented)

`src/squint/repl/nrepl_server.cljs` reads ns-state via `ns-state-map`:

- **info/eldoc/lookup**: `(get-in @ns-state [ns sym])` -> `{:arglists :doc ...}`.
  Only same-session, unqualified vars resolve; core fns and external-lib vars are
  not tracked, so they return `no-info`/`no-eldoc`.
- **complete**: candidates = symbol keys of `(get @ns-state current-ns)` (locals)
  plus `:refers` and `:aliases` keys, prefix-filtered, munged self-aliases
  (containing `_DOT_`) dropped. Core fns (`resources/squint/core.edn`) and
  publics of arbitrary required libs are not enumerated - no runtime ns env.
  For a `js/...` prefix, JS-interop names are enumerated from `globalThis` (its
  prototype chain) - in the browser over the transport (`complete-js` op in
  vite.js) when eval is delegated there, else from node directly (`js-completions`).
  This is the one completion path that does NOT use ns-state; it queries the live
  JS runtime, mirroring sci.nrepl/scittle's browser-side interop completion.
