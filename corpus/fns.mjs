import { get, prn, __destructure_map, keyword, arrayMap } from 'cherry-cljs/cljs.core.js'

const foo = function foo (p__1201) {
return (function () {
 return (function () {
 let map__1202, a, b;
map__1202 = p__1201;
map__1202 = __destructure_map(map__1202);
a = get(map__1202, keyword("a"));
b = get(map__1202, keyword("b"));
return (function () {
 return (a + b);
})();
})();
})();
};
prn(foo(arrayMap(keyword("a"), 1, keyword("b"), 2)));
const bar = function (p__1204) {
return (function () {
 return (function () {
 let map__1205, a, b;
map__1205 = p__1204;
map__1205 = __destructure_map(map__1205);
a = get(map__1205, keyword("a"));
b = get(map__1205, keyword("b"));
return (function () {
 return (a + b);
})();
})();
})();
};
prn(bar(arrayMap(keyword("a"), 1, keyword("b"), 2)));
