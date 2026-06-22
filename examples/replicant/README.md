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

Replicant itself is pulled in as a git dependency via the `:deps` key in
`squint.edn`:

```clojure
{:paths ["src"]
 :deps {io.github.cjohansen/replicant {:git/url "https://github.com/borkdude/replicant"
                                       :git/sha "e8bf46f4604cc9303bf9120b526e52af494373d2"}}}
```

`:deps` uses the same format as `deps.edn`. Source directories are resolved with
the `clojure` CLI (`-Spath`) and added to `:paths`. Only git and `:local/root`
libraries are supported, no jars yet.
