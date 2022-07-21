import { js_obj, prn, nth, vector, __destructure_map, keyword, clj__GT_js, arrayMap } from 'cherry-cljs/cljs.core.js'

(function () {
 let map__1128, m, a, map__1129, b, vec__1130, c, d, e;
map__1128 = clj__GT_js(arrayMap(keyword("a"), 1));
map__1128 = __destructure_map(map__1128);
m = map__1128;
a = map__1128["a"];
map__1129 = js_obj("js/b", keyword("js/b"));
map__1129 = __destructure_map(map__1129);
b = map__1129["js/b"];
vec__1130 = vector(2, 3, 4);
c = nth(vec__1130, 0, null);
d = nth(vec__1130, 1, null);
e = nth(vec__1130, 2, null);
return (function () {
 return prn(m, a, b, c, d, e);
})();
})();
