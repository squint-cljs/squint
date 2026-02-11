# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Squint is a ClojureScript dialect that compiles to JavaScript with minimal bundle size. It uses native JS data structures (objects, arrays, Maps, Sets) instead of ClojureScript's persistent data structures, producing readable ES module output.

The compiler itself is written in Clojure/ClojureScript (`.cljc` files), compiled to JS via shadow-cljs, then runs on Node.js.

## Build & Development Commands

Requires: Node.js, Java (for shadow-cljs), [Babashka](https://babashka.org/) (`bb`)

```bash
bb build              # Full release build (deletes lib/, rebuilds via shadow-cljs)
bb watch              # Dev mode: shadow-cljs watch with test config
bb dev                # Watch + playground server (parallel)
```

### Testing

```bash
bb test:node          # Primary: compile tests via shadow-cljs, run with node lib/squint_tests.js, plus REPL and project tests
bb test:bb            # Run compiler tests in Babashka
bb test:clj           # Run compiler tests in Clojure JVM (clojure -X:test)
bb test:libs          # Run against external projects (clojure-mode)
```

The `bb test:node` pipeline: shadow-cljs compile → `node lib/squint_tests.js` → node REPL tests → test-project integration test.

There is no built-in way to run a single test. Tests use `clojure.test` and live in `test/squint/`. The main test file is `test/squint/compiler_test.cljs`.

## Architecture

### Compilation Pipeline

```
ClojureScript source → edamame (parser) → compiler (emit) → JavaScript string
```

The compiler is a direct string-emitting compiler, not an AST-based one. The main entry point is `emit` in `compiler_common.cljc`, which dispatches on expression type. Special forms are handled by `emit-special` (a multimethod in `compiler.cljc`).

### Key Source Files

- **`src/squint/compiler.cljc`** — Main compiler: special form dispatch (`emit-special`), built-in macro table, `compile-string`
- **`src/squint/compiler_common.cljc`** — Shared compiler state (dynamic vars like `*aliases*`, `*cljs-ns*`, `*async*`), `emit` function, infix operators, return handling
- **`src/squint/core.js`** — JavaScript runtime library (core functions like `assoc`, `get`, `map`, `filter`, etc.)
- **`src/squint/internal/macros.cljc`** — Built-in macro implementations (`->`, `cond`, `doseq`, `for`, `when`, etc.)
- **`src/squint/internal/fn.cljc`** — Function compilation (`fn`, `defn`, multi-arity, variadic args)
- **`src/squint/internal/destructure.cljc`** — Destructuring (`let`, binding forms)
- **`src/squint/internal/loop.cljc`** — `loop`/`recur` compilation
- **`src/squint/defclass.cljc`** — `defclass` special form (JS class emission)
- **`src/squint/internal/deftype.cljc`** — `deftype`/`defprotocol` support
- **`src/squint/internal/cli.cljs`** — CLI entry point (`npx squint`)
- **`src/squint/repl/nrepl_server.cljs`** — nREPL server (experimental)

### Compiler Environment

The `env` map passed through compilation controls emission behavior:
- `:context` — `:expr`, `:return`, or `:statement` (controls whether `return` is emitted)
- `:top-level` — whether at module top level
- `:async` / `:gen` — inside async/generator function

### Data Structure Mapping

| Squint              | JavaScript              |
|---------------------|-------------------------|
| `{:a 1}`            | `{a: 1}`                |
| `[1 2 3]`           | `[1, 2, 3]`             |
| `:foo`              | `"foo"`                 |
| `#{1 2}`            | `new Set([1, 2])`       |
| `assoc`, `conj`     | shallow copy (spread)   |
| `assoc!`, `conj!`   | mutate in place         |

### Test Utilities (`test/squint/test_utils.cljs`)

- `jss!` — compile a form to a JS string
- `js!` — compile and eval, returns `[value js-string]`
- `jsv!` — compile and eval, returns just the value
- `eq` — deep equality via lodash (handles arrays, objects, Maps, Sets)

### shadow-cljs Build

The squint compiler itself is compiled from ClojureScript to JS via shadow-cljs. The build config (`shadow-cljs.edn`) produces ES modules under `lib/` with multiple module splits (compiler, CLI, nREPL, etc.).
