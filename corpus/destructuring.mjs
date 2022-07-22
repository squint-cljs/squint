import { seq, first, js_obj, prn, vector, __destructure_map, keyword, next, clj__GT_js, arrayMap } from 'cherry-cljs/cljs.core.js'

(function () {
 let map__1123, m, a, map__1124, b, vec__1125, seq__1126, first__1127, c, d, e, f;
map__1123 = clj__GT_js(arrayMap(keyword("a"), 1));
map__1123 = __destructure_map(map__1123);
m = map__1123;
a = map__1123["a"];
map__1124 = js_obj("js/b", keyword("js/b"));
map__1124 = __destructure_map(map__1124);
b = map__1124["js/b"];
vec__1125 = vector(2, 3, 4, 5, 6);
seq__1126 = seq(vec__1125);
first__1127 = first(seq__1126);
seq__1126 = next(seq__1126);
c = first__1127;
first__1127 = first(seq__1126);
seq__1126 = next(seq__1126);
d = first__1127;
first__1127 = first(seq__1126);
seq__1126 = next(seq__1126);
e = first__1127;
f = seq__1126;
return (function () {
 return prn(m, a, b, c, d, e, f);
})();
})();
