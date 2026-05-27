var squint_core = await import('squint-cljs/core.js');
var squint_html = await import('squint-cljs/src/squint/html.js');
globalThis.index = globalThis.index || {};
await import('./another.js'); var a = globalThis.another;
var another = globalThis.another;
globalThis.index.a = a;
var hello = function () {
return squint_html.tag`<pre>another/s = ${globalThis.index.a.s}</pre>`;

};
globalThis.index.hello = hello;
window.document.querySelector("#app").innerHTML = globalThis.index.hello();

export { hello }
