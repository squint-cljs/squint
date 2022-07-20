import { toJs, first, dissoc, get, vector, keyword, str, arrayMap } from 'cherry-cljs/cljs.core.js'

const js_map = toJs(arrayMap(keyword("foo"), keyword("bar")));
console.log(js_map);
const clj_map = arrayMap(keyword("foo/bar"), (1 + 2 + 3));
console.log(get(clj_map, keyword("foo/bar")));
console.log(str(clj_map));
const log = console.log;
log(first(vector(1, 2, 3)));
function foo (x) {
return (function () {
 return dissoc(x, keyword("foo"));
})();
};
log(str(foo(arrayMap(keyword("foo"), 1, keyword("bar"), 2))));
