// vite.config.js
import { defineConfig } from 'vite';
import squintRepl from './vite-plugin-squint-repl.js';

// Resolves bare specifiers used inside REPL-eval'd dynamic import()s
// (e.g. import('squint-cljs/core.js')) to vite-served file URLs.
const ResolveDepsPlugin = {
  name: 'resolve-deps',
  async configureServer(server) {
    server.middlewares.use('/@resolve-deps', async (req, res) => {
      const url = req.url.substring(1);
      let file;
      try {
        file = await server.moduleGraph.resolveId(url);
      } catch (e) {
        res.writeHead(404);
        res.end();
        return;
      }
      res.writeHead(302, { location: `/@fs${file.id}` });
      res.end();
    });
  },
};

export default defineConfig(() => {
  return {
    // Pre-bundle deps we may require at the REPL so vite doesn't discover
    // them mid-session (which would re-optimize and reload the page).
    optimizeDeps: { include: ['joi', 'lodash', 'nanoid'] },
    // squintRepl owns the cljs -> js compile (dev watch + build), the REPL
    // transport, and injecting the browser eval listener.
    plugins: [ResolveDepsPlugin, squintRepl()],
  };
});
