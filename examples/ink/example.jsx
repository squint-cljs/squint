import { nth } from 'clavascript/core.js'
import { render, Text } from 'ink';
import * as react from 'react';
var Counter = function () {
let vec__14 = react.useState(0);
let counter5 = nth(vec__14, 0, null);
let setCounter6 = nth(vec__14, 1, null);
react.useEffect(function () {
let timer7 = setInterval(function () {
return setCounter6(function (counter) {
return (counter5 + 1);
});
}, 100);
return function () {
return clearInterval(timer7);
};
}, []);
return react.createElement(Text, counter5, "tests", "passed");
};
render({ react.createElement(Counter) });

export { Counter }
