async function status () {
return (await (async function () {
 return (await (async function () {
 const resp = (await fetch("https://clojure.org"));
const status = (await resp["status"]);
return (function () {
 return status;
})();
})());
})());
};
const chalk = (await import("chalk"))["default"];
console.log("status:", chalk.green((await status())));
