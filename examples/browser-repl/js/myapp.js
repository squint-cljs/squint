var squint_core = await import('squint-cljs/core.js');
globalThis.user = globalThis.user || {};
globalThis.myapp = globalThis.myapp || {};
globalThis.myapp.foobar = function () {
return 5;
};
export const foobar = myapp.foobar;