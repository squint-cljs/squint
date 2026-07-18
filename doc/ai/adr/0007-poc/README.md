# ADR 0007 proof of concept

Experiments backing doc/ai/adr/0007-deferred-core-fn-costs.md.

Mechanism POCs (standalone `node`, self-contained):

- `perf.cjs` - hot-path perf of the dispatch mechanisms (`node perf.cjs`, or
  `EXTENDED=1 node perf.cjs`). Shows the registry hybrid is perf-neutral and
  wrapping regresses once an `extend-type` exists.
- `dce.cjs` - a toy model of whether a protocol-free base lets slot symbols shake
  out (`node dce.cjs`; needs esbuild). NOTE: this only models the symbols, not
  the dispatch-branch code, so it over-states the win - the real measurement
  below corrects it.
- `extend.cjs` - whether a user can still extend a built-in protocol in the
  registry model (`node extend.cjs`).

Real measurement (the one that decided it): transform the shipping 0.14.206
core.js in place, hold the reagami app/lib constant, rebuild, compare gzip.

- `measure-ceiling.cjs` - rewrites every protocol-slot access to `(void 0)` so
  esbuild folds the dispatch branches away AND drops the now-unused symbols.
  Produces the *ceiling* core (all slot dispatch gone).
- `measure-registry.cjs` - collapses the 34 slot symbols to one, keeping the
  dispatch branches - i.e. what a registry actually recovers (symbols only).

Result on the reagami js-framework-benchmark app (gzip):

| variant | gzip | recovers |
|---|---|---|
| baseline (as ships) | 9201 | - |
| registry (symbols gone, branches kept) | 8894 | 307 B |
| ceiling (all slot dispatch gone) | 8301 | 900 B |

Conclusion: a registry recovers ~307 bytes, the ceiling 900; not the ~1.5 KB the
version-to-version delta suggested. Keep the baseline.
