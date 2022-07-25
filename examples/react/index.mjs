import { truth_, keyword_QMARK_, name, nth, vector, keyword, count, apply, clj__GT_js, vector_QMARK_, arrayMap } from 'cherry-cljs/cljs.core.js'
import confetti from 'https://cdn.skypack.dev/canvas-confetti';
import * as react from 'https://cdn.skypack.dev/react';
import { useEffect } from 'https://cdn.skypack.dev/react';
import * as rdom from 'https://cdn.skypack.dev/react-dom';
var $ = function (elt, props, children) {
let props1 = clj__GT_js(props);
let elt2 = (keyword_QMARK_(elt)) ? (name(elt)) : (elt);
if (truth_(vector_QMARK_(children))) {
return apply(react.createElement, elt2, props1, children);} else {
return react.createElement(elt2, props1, children);}
};
var App = function () {
useEffect(function () {
return confetti();
}, vector());
let vec__36 = react.useState(0);
let count7 = nth(vec__36, 0, null);
let setCount8 = nth(vec__36, 1, null);
return $(keyword("div"), null, vector($(keyword("p"), null, vector("You clicked ", count7, " times!")), $(keyword("button"), arrayMap(keyword("onClick"), function () {
confetti();
return setCount8((count7 + 1));
}), "Click me")));
};
rdom.render($(App, null, vector()), document.getElementById("app"));

export { $, App }
