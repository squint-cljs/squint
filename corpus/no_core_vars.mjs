import { vector, str } from 'cherry-cljs/cljs.core.js'

function foo () {
return (function () {
 return vector();
})();
};
console.log(str(foo()));
