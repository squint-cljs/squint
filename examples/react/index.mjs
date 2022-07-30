import { IndexedSeq, first, cons, alength, truth_, keyword_QMARK_, name, map_QMARK_, nth, array, vector, keyword, next, into_array, count, clj__GT_js, arrayMap } from 'cherry-cljs/cljs.core.js'
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
case 2:
return f1.cljs$core$IFn$_invoke$arity$2((arguments[0]), (arguments[1]));
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
f1["cljs$core$IFn$_invoke$arity$2"] = function (elt, props) {
return $(elt, props, null);
};
f1["cljs$core$IFn$_invoke$arity$variadic"] = function (elt, props, children) {
let vec__1619 = (map_QMARK_(props)) ? (vector(clj__GT_js(props), children)) : (vector(null, cons(props, children)));
let props20 = nth(vec__1619, 0, null);
let children21 = nth(vec__1619, 1, null);
let elt22 = (keyword_QMARK_(elt)) ? (name(elt)) : (elt);
return react.createElement(elt22, props20, into_array(children21));
};
f1["cljs$lang$applyTo"] = function (seq13) {
let G__1423 = first(seq13);
let seq1324 = next(seq13);
let G__1525 = first(seq1324);
let seq1326 = next(seq1324);
return this_as(self__22079__auto__, self__22079__auto__.cljs$core$IFn$_invoke$arity$variadic(G__1423, G__1525, seq1326));
};
f1["cljs$lang$maxFixedArity"] = 2;
return f1;
})();
var App = function () {
useEffect(function () {
return confetti();
}, vector());
let vec__2730 = react.useState(0);
let count31 = nth(vec__2730, 0, null);
let setCount32 = nth(vec__2730, 1, null);
return $(keyword("div"), $(keyword("p"), "You clicked ", count31, " times!"), $(keyword("button"), arrayMap(keyword("onClick"), function () {
confetti();
return setCount32((count31 + 1));
}), "Click me"));
};
rdom.render($(App), document.getElementById("app"));

export { $, App }
