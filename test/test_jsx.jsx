import { odd_QMARK_, println } from 'clavascript/core.js'
import { renderToStaticMarkup } from 'react-dom/server';
var App = function () {
let x1 = "dude";
return <a href={x1}>Hello {(1 + 2 + 3)} {x1} {(odd_QMARK_(2)) ? (<div>Yo</div>
) : (<div>Fo</div>
)} <pre><code>(+ 1 2 3)</code>
</pre>
</a>
;
};
println((await renderToStaticMarkup(<App></App>
)));

export { App }
