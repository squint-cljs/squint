const log = console.log;
log("hello");
log((1 + 2 + 3));
let y1 = (function () {
 let x2 = (function () {
 log("in do");
return 12;
})();
log("x + 1 =", (x2 + 1));
return (x2 + 13);
})();
let inc3 = "inc";
log("y =", y1, inc3);

export { log }
