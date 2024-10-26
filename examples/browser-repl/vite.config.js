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
  configureServer(server) {
    console.log(server);
    server.middlewares.use('/@resolve-deps', (req, res, next) => {
      req.url = '/foo';
      // import.meta.resolve('joi');
      console.log(this.resolve('joi'));
      // console.log(fetchModule(env, 'joi'));
      next();
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
