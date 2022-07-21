import { get, nth, vector, __destructure_map, keyword, arrayMap } from 'cherry-cljs/cljs.core.js'

(function () {
 let map__1124, a, vec__1125, b, c, d;
map__1124 = arrayMap(keyword("a"), 1);
map__1124 = __destructure_map(map__1124);
a = get(map__1124, keyword("a"));
vec__1125 = vector(2, 3, 4);
b = nth(vec__1125, 0, null);
c = nth(vec__1125, 1, null);
d = nth(vec__1125, 2, null);
return (function () {
 return console.log(a, b, c, d);
})();
})();
