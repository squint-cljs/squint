// vite.config.js
import { defineConfig } from 'vite';
import squint from 'squint-cljs/vite';

export default defineConfig(() => {
  return {
    // Pre-bundle deps we may require at the REPL so vite doesn't discover them
    // mid-session (which would re-optimize and reload the page).
    optimizeDeps: {
      include: [
        'canvas-confetti',
        'nanoid',
        // preact + its jsx runtimes, used by the :jsx-runtime demo (src/app.cljs)
        'preact',
        'preact/jsx-runtime',
        'preact/jsx-dev-runtime',
      ],
    },
    // The squint vite plugin: compiles cljs -> js (dev watch + build), resolves
    // REPL imports, and runs the browser REPL (nREPL server + eval over the HMR WS).
    plugins: [squint()],
  };
});
