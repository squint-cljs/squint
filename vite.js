// Squint's vite plugin: the shared browser-REPL plugin (vite-common.js) with
// squint's compiler and nREPL server. See vite-common.js for the design notes.

import { compileFile, readConfig, depsPaths } from './node-api.js';
import {
  startServer,
  handleBrowserMessage,
  evalString,
} from './lib/node.nrepl_server.js';
import { makeVitePlugin } from './vite-common.js';

export default makeVitePlugin({
  name: 'squint',
  coreImport: 'squint-cljs/core.js',
  compileFile,
  readConfig,
  depsPaths,
  startServer,
  handleBrowserMessage,
  evalString,
});
