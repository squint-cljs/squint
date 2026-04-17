# cljs.test / clojure.test: known gaps in squint's implementation

This list captures behavior the squint (and cherry) cljs.test built-ins
don't yet match against the canonical `clojure.test` / `cljs.test`. The
ordering is rough priority — high-impact correctness gaps first, then
extensibility gaps, then polish.

## Correctness — likely to bite real users

### 1. Fixtures are global, not per-namespace ✅ DONE
~~`set-each-fixtures!` / `set-once-fixtures!` write into the single global
env. Real `clojure.test` keys fixtures by namespace (`(alter-meta! ns ...)`).
Today, two test namespaces each calling `(use-fixtures :each ...)` will
clobber each other; whichever loaded last wins for *every* test.~~

Fixed: `:each-fixtures` and `:once-fixtures` are now `{ns-str → vec}`
maps; `core-deftest` stashes `:ns` in the test fn's meta; `test-var`
and `run-tests` look fixtures up by that ns. The 1-arg legacy setters
target a `nil`-keyed bucket for back-compat. Regression tests in both
the squint smoke suite and cherry's `cross_platform_test`.

### 2. run-tests doesn't reset counters per call ✅ DONE
~~`(run-tests)` auto-inits the env only when `*current-env*` is `nil`. A
second `(run-tests)` in the same module reuses (and inflates) the
existing counters. Tests that themselves invoke `run-tests` (squint and
cherry's own runtime tests do this) emit summaries showing cumulative
counts, not per-run counts.~~

Fixed: `run-tests` saves the caller's `:report-counters`, runs against
fresh ones, restores on return. The returned summary map reflects only
that run. Inner `run-tests` calls now show their own per-call summary
instead of polluted cumulative totals. Regression tests in both repos.

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

### 7. `:begin-test-ns` / `:end-test-ns` are never emitted ✅ DONE
~~We support the report types in `report`'s `case`, but `run-tests` never
fires them. Real `clojure.test` brackets each ns it processes with these
events; reporters use them to group output.~~

Fixed: `run-vars-with-once-fixtures` now brackets each ns's tests with
`:begin-test-ns` / `:end-test-ns` reports, so `(t/run-tests 'my.ns)`
prints `Testing my.ns` like cljs.test. Anonymous (nil-ns) groups are
skipped. Smoke tests assert the lines appear.

### 8. No `(t/async done body)` form ✅ DONE
~~Real `cljs.test` async tests use `(async done (do ... (done)))`. We use
`^:async` on the deftest plus a Promise-returning body. Functionally
equivalent but different surface — code copied from a CLJS project
won't run.~~

Added: `core-async` macro expands `(async done body)` to
`(js/Promise. (fn [done] body))`. `test-var` already awaits any
Promise-returning test fn, so no additional plumbing was needed —
the `^:async` form keeps working too. Regression tests in both repos.

### 9. Test discovery is registration-based, not metadata-based
`clojure.test/run-tests` walks `ns-publics` and filters by
`(:test (meta var))`. We use a separate `test-registry` because squint
has no var metadata at runtime. That works for tests defined via our
`deftest`, but a user who manually attaches `:test true` to a defn
won't be picked up.

**Note:** unclear this is fixable without a var registry — probably
accept the divergence and document.

### 10. ~~`successful?` reads global counters~~ — non-issue
On re-read: `successful?` takes a counters map and reads `:fail`/`:error`
from it. No global state involved. Removed.

## Polish — cosmetic but worth fixing

### 11. Inner `run-tests` calls produce double summary output ✅ DONE
~~Two of cherry's `cross_platform_test` cases call `run-tests` inside test
bodies (testing the runtime itself). Each emits its own `:summary` line,
on top of the outer test run's summary.~~

Side-effect of #2: each inner `run-tests` now reports its own per-call
summary with accurate counts (instead of cumulative pollution). Still
multiple summary lines, but the lines are correct and informative —
not noise. Closing.

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
