// vite.config.js
import { defineConfig, fetchModule } from 'vite';
import { spawn } from 'node:child_process';

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
      const file = await server.moduleGraph.resolveId(url);
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
    {
      name: 'prebuild-commands',
      buildStart: async () => {
        if ( 'development' === mode ) {
          cmd('squint', 'watch', '--repl');
        }
        else await cmd('squint', 'compile'); },
    },
    ],
  };
});
