import { toJs, get, keyword, assoc } from 'cherry-cljs/cljs.core.js'

const js_map = toJs(assoc(null, keyword("foo"), keyword("bar")));
console.log(js_map);
const clj_map = assoc(null, keyword("foo"), (1 + 2 + 3));
console.log(get(clj_map, keyword("foo")));
