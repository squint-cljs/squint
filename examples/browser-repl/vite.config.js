// vite.config.js
import { defineConfig } from 'vite';
import { spawn } from 'node:child_process';
import * as process from 'node:process';

function cmd(...command) {
  const p = spawn(command[0], command.slice(1));
  return new Promise((resolveFunc) => {
    p.stdout.on("data", (x) => {
      process.stdout.write(x.toString());
    });
    p.stderr.on("data", (x) => {
      process.stderr.write(x.toString());
    });
    p.on("exit", (code) => {
      resolveFunc(code);
    });
  });
}
//
export default defineConfig({
  plugins: [
    {
      name: 'prebuild-commands',
      buildStart: async () => { cmd('squint', 'watch'); },
    },
  ],
});

