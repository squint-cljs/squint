import { js_keys, js_obj, prn } from 'cherry-cljs/cljs.core.js'

function foo () {
return (function () {
 return js_obj("foo", "bar");
})();
};
prn(1);
console.log(js_keys(foo()));
