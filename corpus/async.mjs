const status = async function () {
let resp1 = await(fetch("https://clojure.org"));
let status2 = await(resp1["status"]);
return status2;
};
console.log("status:", await(status()));

export { status }
