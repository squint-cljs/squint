import { prn, nth, vector, __destructure_map, keyword, clj__GT_js, arrayMap } from 'cherry-cljs/cljs.core.js'

(function () {
 let map__1124, m, a, vec__1125, b, c, d;
map__1124 = clj__GT_js(arrayMap(keyword("a"), 1));
map__1124 = __destructure_map(map__1124);
m = map__1124;
a = map__1124["a"];
vec__1125 = vector(2, 3, 4);
b = nth(vec__1125, 0, null);
c = nth(vec__1125, 1, null);
d = nth(vec__1125, 2, null);
return (function () {
 return prn(m, a, b, c, d);
})();
})();
(function () {
 let map__1129, m, a;
map__1129 = arrayMap(keyword("js/a"), 1);
map__1129 = __destructure_map(map__1129);
m = map__1129;
a = map__1129["a"];
return (function () {
 return prn(m, a);
})();
})();
