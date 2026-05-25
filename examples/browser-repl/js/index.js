var squint_core = await import('squint-cljs/core.js');
var squint_html = await import('squint-cljs/src/squint/html.js');
globalThis.index = globalThis.index || {};
var squint_core = await import('squint-cljs/core.js');
var joi = await import('joi');
globalThis.index.squint_core = squint_core;
globalThis.index.joi = joi;
var hello = function () {
return squint_html.html`<pre>Hellox</pre>`;

};
globalThis.index.hello = hello;
window.document.querySelector("#app").innerHTML = globalThis.index.hello();

export { hello }
