var squint_core = await import('squint-cljs/core.js');
globalThis.user = globalThis.user || {};
globalThis.nrepl = globalThis.nrepl || {};
await import('squint-cljs/src/squint/nrepl.js');
