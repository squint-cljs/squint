const foo = function () {
return (function () {
 return "hello";
})();
};
console.log(foo());

export { foo }
