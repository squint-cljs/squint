import { IndexedSeq, first, alength, truth_, keyword_QMARK_, name, map_QMARK_, nth, array, vector, keyword, next, into_array, count, clj__GT_js, arrayMap } from 'cherry-cljs/cljs.core.js'
import confetti from 'https://cdn.skypack.dev/canvas-confetti';
import * as react from 'https://cdn.skypack.dev/react';
import { useEffect } from 'https://cdn.skypack.dev/react';
import * as rdom from 'https://cdn.skypack.dev/react-dom';
var $ = (function () {
 let f1 = function (var_args) {
let G__56 = alength(arguments);
switch (G__56) {case 1:
return f1.cljs$core$IFn$_invoke$arity$1((arguments[0]));
break;
default:
let args_arr38 = array();
let len__22076__auto__9 = alength(arguments);
let i410 = 0;
while(true){
if (truth_((i410 < len__22076__auto__9))) {
args_arr38.push((arguments[i410]));
let G__11 = (i410 + 1);
i410 = G__11;
continue;
};break;
}
;
let argseq__22117__auto__12 = new IndexedSeq(args_arr38.slice(2), 0, null);
return f1.cljs$core$IFn$_invoke$arity$variadic((arguments[0]), (arguments[1]), argseq__22117__auto__12);}
};
f1["cljs$core$IFn$_invoke$arity$1"] = function (elt) {
return $(elt, null);
};
f1["cljs$core$IFn$_invoke$arity$variadic"] = function (elt, props, children) {
let elt16 = (keyword_QMARK_(elt)) ? (name(elt)) : (elt);
if (truth_(map_QMARK_(props))) {
return react.createElement(elt16, clj__GT_js(props), into_array(children));} else {
return react.createElement(elt16, ({  }), props, 1, 2, 3, into_array(children));}
};
f1["cljs$lang$applyTo"] = function (seq13) {
let G__1417 = first(seq13);
let seq1318 = next(seq13);
let G__1519 = first(seq1318);
let seq1320 = next(seq1318);
return this_as(self__22079__auto__, self__22079__auto__.cljs$core$IFn$_invoke$arity$variadic(G__1417, G__1519, seq1320));
};
f1["cljs$lang$maxFixedArity"] = 2;
return f1;
})();
var App = function () {
useEffect(function () {
return confetti();
}, vector());
let vec__2124 = react.useState(0);
let count25 = nth(vec__2124, 0, null);
let setCount26 = nth(vec__2124, 1, null);
return $(keyword("div"), $(keyword("p"), "You clicked ", count25, " times!"), $(keyword("button"), arrayMap(keyword("onClick"), function () {
confetti();
return setCount26((count25 + 1));
}), "Click me"));
};
rdom.render($(App), document.getElementById("app"));

export { $, App }
