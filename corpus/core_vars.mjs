import { clj__GT_js, keyword, arrayMap, get, str, first, vector, dissoc } from 'cherry-cljs/cljs.core.js'
const js_map = clj__GT_js(arrayMap(keyword("foo"), keyword("bar")));
console.log(js_map);
const clj_map = arrayMap(keyword("foo/bar"), (1 + 2 + 3));
console.log(get(clj_map, keyword("foo/bar")));
console.log(str(clj_map));
const log = console.log;
log(first(vector(1, 2, 3)));
const foo = function (x) {
return dissoc(x, keyword("foo"));
};

export { js_map, clj_map, log, foo }
