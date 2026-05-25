// vite.config.js
import { defineConfig, fetchModule } from 'vite';
import { spawn } from 'node:child_process';
import squintRepl from './vite-plugin-squint-repl.js';

function cmd(...command) {
  const p = spawn(command[0], command.slice(1), { stdio: 'inherit' });
  return new Promise((resolveFunc) => {
    p.on("exit", (code) => {
      resolveFunc(code);
    });
  });
}

const ResolveDepsPlugin = {
  name: 'resolve-deps',
  async configureServer(server) {
    server.middlewares.use('/@resolve-deps', async (req, res, next) => {
      const url = req.url.substring(1);
      var file;
      try {
        file = await server.moduleGraph.resolveId(url);
      }
      catch (e) {
        res.writeHead(404);
        res.end();
        return;
      }
      console.log('url', url, 'file', file);
      const newUrl = `/@fs${file.id}`;
        res.writeHead(302, {
          location: newUrl,
        });
        res.end();
        return;
    });
  }
};

export default defineConfig( ({mode}) => {
  console.log('mode', mode);
  return {
    plugins: [
      ResolveDepsPlugin,
      squintRepl(),
    {
      name: 'prebuild-commands',
      buildStart: async () => {
        // In dev, run `squint watch --repl` yourself (see the dev script).
        // Only compile once for production builds.
        if ( 'development' !== mode ) await cmd('squint', 'compile'); },
    },
    ],
  };
});
