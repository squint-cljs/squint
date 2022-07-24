import { __destructure_map, get, keyword, prn, arrayMap } from 'cherry-cljs/cljs.core.js'
const foo = function foo (p__1) {
let map__23 = p__1;
let map__24 = __destructure_map(map__23);
let a5 = get(map__24, keyword("a"));
let b6 = get(map__24, keyword("b"));
return (a5 + b6);
};
prn(foo(arrayMap(keyword("a"), 1, keyword("b"), 2)));
const bar = function (p__7) {
let map__89 = p__7;
let map__810 = __destructure_map(map__89);
let a11 = get(map__810, keyword("a"));
let b12 = get(map__810, keyword("b"));
return (a11 + b12);
};
prn(bar(arrayMap(keyword("a"), 1, keyword("b"), 2)));
const baz = function (p__13) {
let map__1415 = p__13;
let map__1416 = __destructure_map(map__1415);
let a17 = map__1416["a"];
let b18 = map__1416["b"];
return (a17 + b18);
};
prn(baz({ "a": 1, "b": 2 }));

export { foo, bar, baz }
