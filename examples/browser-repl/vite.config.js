// vite.config.js
import { defineConfig } from 'vite';
import squintRepl from './vite-plugin-squint-repl.js';

export default defineConfig(() => {
  return {
    // Pre-bundle deps we may require at the REPL so vite doesn't discover
    // them mid-session (which would re-optimize and reload the page).
    optimizeDeps: { include: ['joi', 'lodash', 'nanoid'] },
    // squintRepl owns the cljs -> js compile (dev watch + build), dep
    // resolution for REPL imports, the REPL transport, and injecting the
    // browser eval listener.
    plugins: [squintRepl()],
  };
});
