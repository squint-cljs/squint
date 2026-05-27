# browser-repl example

A working setup for the squint **browser REPL**: `vite dev` compiles your
ClojureScript, hot-reloads the page, and runs an nREPL server you connect your
editor to.

```
npm install
npx vite dev
```

Then connect your editor to `.nrepl-port` (or `localhost:1339`) and eval in the
running page.

Full guide: [`doc/browser-repl.md`](../../doc/browser-repl.md).
