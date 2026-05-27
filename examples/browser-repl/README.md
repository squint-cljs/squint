# browser-repl example

A working setup for the squint **browser REPL**: `vite dev` compiles your
ClojureScript, hot-reloads the page, and runs an nREPL server you connect your
editor to.

```
npm install
npm run dev
```

Then connect your editor to `.nrepl-port` (or `localhost:1339`) and eval in the
running page.

It renders three ways: plain squint (`#html`, `src/index.cljs`), preact via
`:jsx-runtime` (`#jsx`, `src/app.cljs`), and [reagami](https://github.com/borkdude/reagami)
hiccup (`src/reagami_app.cljs`).

Full guide: [`doc/browser-repl.md`](../../doc/browser-repl.md).
