import { get, js_obj, prn, __destructure_map, keyword, arrayMap } from 'cherry-cljs/cljs.core.js'

const foo = function foo (p__1204) {
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
prn(foo(arrayMap(keyword("a"), 1, keyword("b"), 2)));
const bar = function (p__1207) {
return (function () {
 return (function () {
 let map__1208, a, b;
map__1208 = p__1207;
map__1208 = __destructure_map(map__1208);
a = get(map__1208, keyword("a"));
b = get(map__1208, keyword("b"));
return (function () {
 return (a + b);
})();
})();
})();
};
prn(bar(arrayMap(keyword("a"), 1, keyword("b"), 2)));
const baz = function (p__1210) {
return (function () {
 return (function () {
 let map__1211, a, b;
map__1211 = p__1210;
map__1211 = __destructure_map(map__1211);
a = map__1211["a"];
b = map__1211["b"];
return (function () {
 return (a + b);
})();
})();
})();
};
prn(baz(js_obj("a", 1, "b", 2)));
