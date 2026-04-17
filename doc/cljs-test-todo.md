# cljs.test / clojure.test: known gaps in squint's implementation

This list captures behavior the squint (and cherry) cljs.test built-ins
don't yet match against the canonical `clojure.test` / `cljs.test`. The
ordering is rough priority — high-impact correctness gaps first, then
extensibility gaps, then polish.

## Correctness — likely to bite real users

### 1. Fixtures are global, not per-namespace
`set-each-fixtures!` / `set-once-fixtures!` write into the single global
env. Real `clojure.test` keys fixtures by namespace (`(alter-meta! ns ...)`).
Today, two test namespaces each calling `(use-fixtures :each ...)` will
clobber each other; whichever loaded last wins for *every* test.

**Fix sketch:** key `:each-fixtures` / `:once-fixtures` by ns string in the
env; `core-use-fixtures` macro captures `(&env :ns :name str)` and
includes it in the `set-*-fixtures!` call; `test-var` looks the test fn's
ns up via the same metadata `register-test!` already attaches, then
fetches the right fixture vector.

### 2. run-tests doesn't reset counters per call
`(run-tests)` auto-inits the env only when `*current-env*` is `nil`. A
second `(run-tests)` in the same module reuses (and inflates) the
existing counters. Tests that themselves invoke `run-tests` (squint and
cherry's own runtime tests do this) emit summaries showing cumulative
counts, not per-run counts.

**Fix sketch:** snapshot counters at start, restore (or delta) before
emitting summary. Or always init a fresh env at the top of `run-tests`
unless the caller opts out.

### 3. Quoted-symbol emission breaks `is` reporting
The shared macro `(:name '~name)` would expand to `cljs.core.symbol(...)`
under squint, which doesn't exist at runtime. We worked around it by
storing `:name` as a string (and `assert-expr` uses `(pr-str form)`).
Result: `(:name (meta test-fn))` is a string, not a symbol — diverges
from `clojure.test`'s expectations and breaks code that calls
`namespace`/`name` on it.

**Fix sketch:** fix squint's quote emission to route through
`squint_core.symbol` (already a real fn) instead of the literal
`cljs.core.symbol`. Then revert macros to symbol form for parity with
cherry/cljs.test.

## Extensibility — currently impossible to extend

### 4. `assert-expr` is hardcoded
Real `clojure.test/assert-expr` is a multimethod keyed on the head of
the form inside `(is ...)`. Users add new assertions by `defmethod`-ing
it. Ours is a `case` over `=`, `thrown?`, `thrown-with-msg?`. No
extension point.

**Fix sketch:** make `assert-expr` a multimethod (or a registry of
`{op-sym (fn [msg form] ...)}`) that the macro consults. Squint can ship
the existing four cases as default methods.

### 5. `report` is hardcoded too
`clojure.test/report` is a multimethod keyed on `:type`; users add
methods to extend reporting (e.g. cljs-test-display). Ours is a single
`case` defn.

**Fix sketch:** convert to a multimethod (works in squint's runtime; we
already use `(use-fixtures ...)` etc.).

### 6. `test-var` is a plain fn
`clojure.test/test-var` is a multimethod (`:default` impl is what
everyone uses, but it's overridable). Same idea as `report`.

## Coverage gaps — features that exist but are partial

### 7. `:begin-test-ns` / `:end-test-ns` are never emitted
We support the report types in `report`'s `case`, but `run-tests` never
fires them. Real `clojure.test` brackets each ns it processes with these
events; reporters use them to group output.

### 8. No `(t/async done body)` form
Real `cljs.test` async tests use `(async done (do ... (done)))`. We use
`^:async` on the deftest plus a Promise-returning body. Functionally
equivalent but different surface — code copied from a CLJS project
won't run.

### 9. Test discovery is registration-based, not metadata-based
`clojure.test/run-tests` walks `ns-publics` and filters by
`(:test (meta var))`. We use a separate `test-registry` because squint
has no var metadata at runtime. That works for tests defined via our
`deftest`, but a user who manually attaches `:test true` to a defn
won't be picked up.

**Note:** unclear this is fixable without a var registry — probably
accept the divergence and document.

### 10. `successful?` reads global counters
`(successful? results)` is meant to take the *return value* of
`run-tests` (a counters map) and tell you if it passed. Today many of
our internal call sites pass `(:report-counters (get-current-env))`
which is global state. After our run-tests rewrite returns counters
properly this is mostly fine, but the helper still reads the env when
called with no/incompatible args.

## Polish — cosmetic but worth fixing

### 11. Inner `run-tests` calls produce double summary output
Two of cherry's `cross_platform_test` cases call `run-tests` inside test
bodies (testing the runtime itself). Each emits its own `:summary` line,
on top of the outer test run's summary. Visual noise; not a correctness
issue.

**Fix sketch:** add a `{:summary? false}` opts arg to `run-tests`, or
drop the auto-summary and require an explicit `(report {:type :summary})`
at the call site (matching the very first version we had).

### 12. `:report-counters :summary` increments needlessly
`report` calls `(inc-report-counter! :type)` unconditionally, including
for `:type :summary`. Adds a meaningless counter to the env map.

### 13. No `*test-out*` redirection
Output goes hard-wired to `js/console.log`/`js/console.error`. Real
`cljs.test` lets users rebind via `*report-out*` or per-method.

### 14. Cherry runtime and squint runtime drifted in subtle ways
Both implement the same surface but are separate `.cljs` files. Easy to
fix one and forget the other. Could share via a `.cljc` if we factor
out the few JS-vs-CLJS differences.
