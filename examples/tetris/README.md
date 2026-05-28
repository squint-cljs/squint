# tetris

Tetris in squint + [reagami](https://github.com/borkdude/reagami). Single-file
game (`src/tetris.cljs`) you can also paste into the squint playground - it
loads reagami from esm.sh and creates the host `#tetris` div on demand.

**Play in the playground (loads this file from GitHub):**
https://squint-cljs.github.io/squint/?src=https://raw.githubusercontent.com/squint-cljs/squint/refs/heads/main/examples/tetris/src/tetris.cljs

Run locally:

```
npm install
npm run dev
```

Controls: arrows move/rotate, space hard drop, p pause, r reset.
