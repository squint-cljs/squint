import * as squint_core from 'squint-cljs/core.js';
import * as squint_html from 'squint-cljs/src/squint/html.js';
import * as joi from 'joi';
import * as m from './myapp.js';
import * as myapp from './myapp.js';
squint_core.prn(m.foobar());
var hello = function () {
return squint_html.html`<pre>Dude</pre>`;

};
window.document.querySelector("#app").innerHTML = hello();

export { hello }
