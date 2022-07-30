import { IndexedSeq, first, cons, alength, truth_, keyword_QMARK_, name, map_QMARK_, nth, array, vector, keyword, next, into_array, count, clj__GT_js, arrayMap } from 'cherry-cljs/cljs.core.js'
import confetti from 'https://cdn.skypack.dev/canvas-confetti';
import * as react from 'https://cdn.skypack.dev/react';
import { useEffect } from 'https://cdn.skypack.dev/react';
import * as rdom from 'https://cdn.skypack.dev/react-dom';
var $ = (function () {
 let f1 = function (var_args) {
let args27 = array();
let len__22076__auto__8 = alength(arguments);
let i39 = 0;
while(true){
if (truth_((i39 < len__22076__auto__8))) {
args27.push((arguments[i39]));
let G__10 = (i39 + 1);
i39 = G__10;
continue;
};break;
}
;
let argseq__22197__auto__11 = ((2 < alength(args27))) ? (new IndexedSeq(args27.slice(2), 0, null)) : (null);
return f1.cljs$core$IFn$_invoke$arity$variadic((arguments[0]), (arguments[1]), argseq__22197__auto__11);
};
f1["cljs$core$IFn$_invoke$arity$variadic"] = function (elt, props, children) {
let vec__1215 = (map_QMARK_(props)) ? (vector(clj__GT_js(props), children)) : (vector(null, cons(props, children)));
let props16 = nth(vec__1215, 0, null);
let children17 = nth(vec__1215, 1, null);
let elt18 = (keyword_QMARK_(elt)) ? (name(elt)) : (elt);
return react.createElement(elt18, props16, into_array(children17));
};
f1["cljs$lang$maxFixedArity"] = 2;
f1["cljs$lang$applyTo"] = function (seq4) {
let G__519 = first(seq4);
let seq420 = next(seq4);
let G__621 = first(seq420);
let seq422 = next(seq420);
return this_as(self__22079__auto__, self__22079__auto__.cljs$core$IFn$_invoke$arity$variadic(G__519, G__621, seq422));
};
return f1;
})();
var App = function () {
useEffect(function () {
return confetti();
}, vector());
let vec__2326 = react.useState(0);
let count27 = nth(vec__2326, 0, null);
let setCount28 = nth(vec__2326, 1, null);
return $(keyword("div"), $(keyword("p"), "You clicked ", count27, " times!"), $(keyword("button"), arrayMap(keyword("onClick"), function () {
confetti();
return setCount28((count27 + 1));
}), "Click me"));
};
rdom.render($(App, null, vector()), document.getElementById("app"));

export { $, App }
