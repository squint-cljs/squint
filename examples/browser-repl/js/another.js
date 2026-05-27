var squint_core = await import('squint-cljs/core.js');
globalThis.another = globalThis.another || {};
var s = "v1";
globalThis.another.s = s;

export { s }
