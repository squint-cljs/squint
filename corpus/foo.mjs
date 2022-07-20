console.log("hello");
console.log((1 + 2 + 3));
(function () { const x = (function () { console.log("in do");
return 12; })();
return (function () { return console.log("x + 1 =", (x + 1)); })(); })();
