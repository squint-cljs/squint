import * as squint_core from 'squint-cljs/core.js';
import * as squint_html from 'squint-cljs/src/squint/html.js';
var hello = function () {
return squint_html.html`<pre>Hello</pre>`;
};
window.document.querySelector("#app").innerHTML = hello();

export { hello }
