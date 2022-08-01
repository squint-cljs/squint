import { vector, nth, count, clj__GT_js, keyword, arrayMap } from 'cherry-cljs/cljs.core.js'
import confetti from 'https://cdn.skypack.dev/canvas-confetti';
import * as react from 'https://cdn.skypack.dev/react';
import { useEffect } from 'https://cdn.skypack.dev/react';
import * as rdom from 'https://cdn.skypack.dev/react-dom';
var App = function () {
useEffect(function () {
return confetti();
}, vector());
let vec__2831 = react.useState(0);
let count32 = nth(vec__2831, 0, null);
let setCount33 = nth(vec__2831, 1, null);
return react.createElement("div", null, react.createElement("p", null, "You clicked ", count32, " times!"), react.createElement("button", clj__GT_js(arrayMap(keyword("onClick"), function () {
confetti();
return setCount33((count32 + 1));
})), "Click me"));
};
rdom.render(react.createElement(App, null, null), document.getElementById("app"));

export { App }
