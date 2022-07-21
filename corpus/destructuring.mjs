import { seq, first, js_obj, prn, vector, __destructure_map, keyword, next, clj__GT_js, arrayMap } from 'cherry-cljs/cljs.core.js'

(function () {
 let map__1204, m, a, map__1205, b, vec__1206, seq__1207, first__1208, c, d, e, f;
map__1204 = clj__GT_js(arrayMap(keyword("a"), 1));
map__1204 = __destructure_map(map__1204);
m = map__1204;
a = map__1204["a"];
map__1205 = js_obj("js/b", keyword("js/b"));
map__1205 = __destructure_map(map__1205);
b = map__1205["js/b"];
vec__1206 = vector(2, 3, 4, 5, 6);
seq__1207 = seq(vec__1206);
first__1208 = first(seq__1207);
seq__1207 = next(seq__1207);
c = first__1208;
first__1208 = first(seq__1207);
seq__1207 = next(seq__1207);
d = first__1208;
first__1208 = first(seq__1207);
seq__1207 = next(seq__1207);
e = first__1208;
f = seq__1207;
return (function () {
 return prn(m, a, b, c, d, e, f);
})();
})();
