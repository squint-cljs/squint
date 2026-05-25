// Minimal squint browser REPL transport over vite's HMR WebSocket.
//
// Design (compare with the standalone-WebSocketServer approach on the
// browser-repl-on-main branch): there is no second WebSocket server. REPL
// eval rides vite's own dev-server WS (import.meta.hot), so reconnection and
// the module graph are owned by vite and stay consistent with hot reload.
//
// For now eval is triggered over a dev-only HTTP endpoint (POST /__repl_eval)
// so we can drive it with curl before wiring a real nREPL/editor relay.

import { compileStringEx } from 'squint-cljs';
import { randomUUID } from 'node:crypto';

const VIRTUAL_CLIENT = 'virtual:squint-repl-client';
const RESOLVED_CLIENT = '\0' + VIRTUAL_CLIENT;

// Browser-side listener, served as a virtual module so the page imports it
// and gets a real import.meta.hot context.
function clientCode() {
  return `
if (import.meta.hot) {
  import.meta.hot.on('squint:eval', async ({ id, code }) => {
    // bare dynamic imports in eval'd code go through vite's resolver
    const rewritten = code.replace(/import\\('(.+?)'\\)/g, "import('/@resolve-deps/$1')");
    let value, err;
    try {
      value = await eval(rewritten);
    } catch (e) {
      err = e && e.message ? e.message : String(e);
    }
    import.meta.hot.send('squint:eval-result', {
      id,
      value: value === undefined ? 'nil' : String(value),
      err,
    });
  });
  console.info('[squint-repl] eval listener ready');
}
`;
}

export default function squintRepl() {
  // REPL compiler state, threaded across evals so ns/defs accumulate.
  let state = {};
  const pending = new Map();

  return {
    name: 'squint-repl',

    resolveId(id) {
      if (id === VIRTUAL_CLIENT) return RESOLVED_CLIENT;
    },
    load(id) {
      if (id === RESOLVED_CLIENT) return clientCode();
    },
    transformIndexHtml() {
      return [
        {
          tag: 'script',
          // vite serves virtual modules under /@id/, encoding the leading
          // null byte of the resolved id as __x00__
          attrs: { type: 'module', src: '/@id/__x00__' + VIRTUAL_CLIENT },
          injectTo: 'head',
        },
      ];
    },

    configureServer(server) {
      server.ws.on('squint:eval-result', (data) => {
        const resolve = pending.get(data.id);
        if (resolve) {
          pending.delete(data.id);
          resolve(data);
        }
      });

      server.middlewares.use('/__repl_eval', async (req, res) => {
        if (req.method !== 'POST') {
          res.writeHead(405);
          res.end();
          return;
        }
        let body = '';
        for await (const chunk of req) body += chunk;

        let js;
        try {
          const out = compileStringEx(
            body,
            { repl: true, context: 'return', elide_exports: true, async: true },
            state,
          );
          state = out;
          js = `(async function () {\n${out.javascript}\n})()`;
        } catch (e) {
          res.writeHead(400, { 'content-type': 'application/json' });
          res.end(JSON.stringify({ err: 'compile error: ' + (e.message || String(e)) }));
          return;
        }

        if (server.ws.clients.size === 0) {
          res.writeHead(503, { 'content-type': 'application/json' });
          res.end(JSON.stringify({ err: 'no browser connected' }));
          return;
        }

        const id = randomUUID();
        const result = new Promise((resolve) => {
          pending.set(id, resolve);
          setTimeout(() => {
            if (pending.has(id)) {
              pending.delete(id);
              resolve({ id, err: 'timeout: no response from browser (10s)' });
            }
          }, 10000);
        });

        server.ws.send('squint:eval', { id, code: js });
        const out = await result;
        res.writeHead(200, { 'content-type': 'application/json' });
        res.end(JSON.stringify({ value: out.value, err: out.err, ns: state.ns }));
      });
    },
  };
}
