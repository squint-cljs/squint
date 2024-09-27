// vite.config.js
import { defineConfig } from 'vite';
import { spawn } from 'node:child_process';

function cmd(...command) {
  const p = spawn(command[0], command.slice(1), { stdio: 'inherit' });
  return new Promise((resolveFunc) => {
    p.on("exit", (code) => {
      resolveFunc(code);
    });
  });
}

export default defineConfig( ({mode}) => {
  console.log('mode', mode);
  return {
    plugins: [
    {
      name: 'prebuild-commands',
      buildStart: async () => {
        if ( 'development' === mode ) {
          cmd('squint', 'watch', '--repl');
        }
        else await cmd('squint', 'compile'); },
    },
  ]};
});
