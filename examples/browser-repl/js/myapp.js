var squint_core = await import('squint-cljs/core.js');
globalThis.myapp = globalThis.myapp || {};
var foobar = function () {
return 5;

};
globalThis.myapp.foobar = foobar;

export { foobar }
