import { seq, first, js_obj, prn, vector, __destructure_map, keyword, next, clj__GT_js, arrayMap } from 'cherry-cljs/cljs.core.js'
(function () {
 let map__1, m, a, map__2, b, vec__3, seq__4, first__5, c, d, e, f;
map__1 = clj__GT_js(arrayMap(keyword("a"), 1));
map__1 = __destructure_map(map__1);
m = map__1;
a = map__1["a"];
map__2 = js_obj("js/b", keyword("js/b"));
map__2 = __destructure_map(map__2);
b = map__2["js/b"];
vec__3 = vector(2, 3, 4, 5, 6);
seq__4 = seq(vec__3);
first__5 = first(seq__4);
seq__4 = next(seq__4);
c = first__5;
first__5 = first(seq__4);
seq__4 = next(seq__4);
d = first__5;
first__5 = first(seq__4);
seq__4 = next(seq__4);
e = first__5;
f = seq__4;
return (function () {
 return prn(m, a, b, c, d, e, f);
})();
})();
