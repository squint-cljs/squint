var squint_core = await import('squint-cljs/core.js');
var squint_html = await import('squint-cljs/src/squint/html.js');
globalThis.index = globalThis.index || {};
var joi = await import('joi');
var m = await import('./myapp.js');
var myapp = await import('./myapp.js');
globalThis.index.joi = joi;
globalThis.index.m = m;
squint_core.prn(globalThis.index.m.foobar());
var hello = function () {
return squint_html.html`<pre>xDude</pre>`;

};
globalThis.index.hello = hello;
window.document.querySelector("#app").innerHTML = globalThis.index.hello();

export { hello }
