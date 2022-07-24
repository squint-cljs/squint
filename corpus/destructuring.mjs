import { seq, first, js_obj, prn, vector, __destructure_map, keyword, next, clj__GT_js, arrayMap } from 'cherry-cljs/cljs.core.js'
let map__16 = clj__GT_js(arrayMap(keyword("a"), 1));
let map__17 = __destructure_map(map__16);
let m8 = map__17;
let a9 = map__17["a"];
let map__210 = js_obj("js/b", keyword("js/b"));
let map__211 = __destructure_map(map__210);
let b12 = map__211["js/b"];
let vec__313 = vector(2, 3, 4, 5, 6);
let seq__414 = seq(vec__313);
let first__515 = first(seq__414);
let seq__416 = next(seq__414);
let c17 = first__515;
let first__518 = first(seq__416);
let seq__419 = next(seq__416);
let d20 = first__518;
let first__521 = first(seq__419);
let seq__422 = next(seq__419);
let e23 = first__521;
let f24 = seq__422;
prn(m8, a9, b12, c17, d20, e23, f24);

export {  }
