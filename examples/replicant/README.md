# replicant example

[Replicant](https://github.com/cjohansen/replicant) running under squint, served
by vite.

```
npm install
npm run dev
```

The squint vite plugin compiles the ClojureScript, hot-reloads the page, and
runs a browser nREPL you can connect your editor to (`.nrepl-port` or
`localhost:1339`).

`src/ohm.cljs` is the Ohm's law calculator from replicant's own dev demos
(`dev/replicant/ohm.cljs`), copied verbatim. `src/app.cljs` is a trimmed copy of
the upstream demo runner (`dev/replicant/dev.cljs`): data-driven event handlers
(`:on {:input [[:actions/...]]}`) dispatched through `d/set-dispatch!`, state in
an atom, re-render on change.

Replicant itself is compiled from a local checkout via `squint.edn`:

```clojure
{:paths ["src" "../../../replicant/src"]}
```

Point that path at your own replicant checkout if it lives elsewhere.
