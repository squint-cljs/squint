var squint_core = await import('squint-cljs/core.js');
globalThis.myapp = globalThis.myapp || {};
var foobar = function () {
return 6;

};
globalThis.myapp.foobar = foobar;
globalThis.myapp.foobar();

export { foobar }
