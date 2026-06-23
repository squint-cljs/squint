# Replicant example

[Replicant](https://github.com/cjohansen/replicant) running under squint, served
by vite. The app is the official Replicant getting-started tutorial,
[tic-tac-toe](https://github.com/cjohansen/replicant-tic-tac-toe).

```
npm install
npm run dev
```

The squint vite plugin compiles the ClojureScript, hot-reloads the page, and
runs a browser nREPL you can connect your editor to (`.nrepl-port` or
`localhost:1339`).

`src/tic_tac_toe/{game.cljc,ui.cljc,core.cljs}` are copied from the tutorial.
`src/tic_tac_toe/main.cljs` is the squint entry point (the tutorial boots from
`dev/` via shadow's `:dev/after-load`).

Three spots were adapted to squint's data model:

- `next-player` and `player->mark` are maps used as functions in CLJS
  (`(next-player p)`); squint maps are plain JS objects, so these use `get`.
- `tics` is looked up the same way (`(map #(get tics %) path)`).
- the winning-path highlight uses a set of `[y x]` vectors in CLJS; squint sets
  compare by reference, so it compares against the path with `=` instead.

Maps keyed by `[y x]` vectors do work: squint stringifies the key consistently,
so `assoc-in`/`get-in` round-trip.

Replicant itself is pulled in as a git dependency via the `:deps` key in
`squint.edn`:

```clojure
{:paths ["src"]
 :deps {io.github.cjohansen/replicant {:git/sha "<latest-sha>"}}}
```

`:deps` uses the same format as `deps.edn`. Source directories are resolved with
the `clojure` CLI (using `-Spath`) and implicitly added to `:paths`. Only git and `:local/root`
libraries are supported right now.
