const foo = function () {
return (function () {
 return "Hello";
})();
};
const default$ = function () {
return (function () {
 return "Default";
})();
};

export { foo }
export default default$
