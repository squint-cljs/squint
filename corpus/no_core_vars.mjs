function foo () {
return (function () {
 return "hello";
})();
};
console.log(foo());
