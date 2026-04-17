import * as squint_core from 'squint-cljs/core.js';
import * as clojure_DOT_string from 'squint-cljs/src/squint/string.js';
var _STAR_current_env_STAR_ = null;
var empty_env = function () {
return ({"report-counters": ({"test": 0, "pass": 0, "fail": 0, "error": 0}), "testing-vars": squint_core.list(), "testing-contexts": squint_core.list(), "once-fixtures": ({}), "each-fixtures": ({})});

};
var get_current_env = function () {
const or__29759__auto__1 = _STAR_current_env_STAR_;
if (squint_core.truth_(or__29759__auto__1)) {
return or__29759__auto__1} else {
return empty_env()};

};
var set_env_BANG_ = function (env) {
_STAR_current_env_STAR_ = env;
return env;

};
var clear_env_BANG_ = function () {
_STAR_current_env_STAR_ = null;
return null;

};
var update_current_env_BANG_ = (() => {
const f1 = (function (var_args) {
const args21 = [];
const len__29484__auto__2 = arguments.length;
let i33 = 0;
while(true){
if ((i33 < len__29484__auto__2)) {
args21.push((arguments[i33]));
let G__4 = (i33 + 1);
i33 = G__4;
continue;
};break;
}
;
const argseq__29668__auto__5 = (((2 < args21.length)) ? (args21.slice(2)) : (null));
return f1.cljs$core$IFn$_invoke$arity$variadic((arguments[0]), (arguments[1]), argseq__29668__auto__5);

});
f1.cljs$core$IFn$_invoke$arity$variadic = (function (ks, f, args) {
const env6 = get_current_env();
const new_env7 = squint_core.apply(squint_core.update_in, env6, ks, f, args);
return set_env_BANG_(new_env7);

});
f1.cljs$lang$maxFixedArity = 2;
return f1;

})();
var testing_contexts_str = function () {
const temp__29412__auto__1 = squint_core.seq(squint_core.get(get_current_env(), "testing-contexts"));
if (squint_core.truth_(temp__29412__auto__1)) {
const contexts2 = temp__29412__auto__1;
return clojure_DOT_string.join(" ", squint_core.reverse(contexts2));
};

};
var testing_vars_str = function () {
const temp__29412__auto__1 = squint_core.seq(squint_core.get(get_current_env(), "testing-vars"));
if (squint_core.truth_(temp__29412__auto__1)) {
const vars2 = temp__29412__auto__1;
return clojure_DOT_string.join(" ", squint_core.map(squint_core.str, vars2));
};

};
var inc_report_counter_BANG_ = function (name) {
if (squint_core.truth_(squint_core.get(get_current_env(), "report-counters"))) {
return update_current_env_BANG_(["report-counters", name], squint_core.fnil(squint_core.inc, 0));
};

};
var current_test_str = function () {
const vars1 = testing_vars_str();
const ctx2 = testing_contexts_str();
if (squint_core.truth_((() => {
const and__29793__auto__3 = vars1;
if (squint_core.truth_(and__29793__auto__3)) {
return ctx2} else {
return and__29793__auto__3};

})())) {
return `${vars1??''}${" "}${ctx2??''}`} else {
if (squint_core.truth_(vars1)) {
return vars1} else {
if (squint_core.truth_(ctx2)) {
return ctx2} else {
if ("else") {
return "test"} else {
return null}}}};

};
var report = function (p__5) {
const map__12 = p__5;
const m3 = map__12;
const type4 = squint_core.get(map__12, "type");
const message5 = squint_core.get(map__12, "message");
const expected6 = squint_core.get(map__12, "expected");
const actual7 = squint_core.get(map__12, "actual");
const line8 = squint_core.get(map__12, "line");
const column9 = squint_core.get(map__12, "column");
const file10 = squint_core.get(map__12, "file");
if (squint_core.truth_(squint_core.contains_QMARK_((new Set (["pass", "fail", "error"])), type4))) {
inc_report_counter_BANG_(type4)};
const location11 = ((squint_core.truth_((() => {
const or__29759__auto__12 = line8;
if (squint_core.truth_(or__29759__auto__12)) {
return or__29759__auto__12} else {
const or__29759__auto__13 = column9;
if (squint_core.truth_(or__29759__auto__13)) {
return or__29759__auto__13} else {
return file10};
};

})())) ? (`${((squint_core.truth_(file10)) ? (`${file10??''}${":"}`) : (null))??''}${((squint_core.truth_(line8)) ? (line8) : (null))??''}${((squint_core.truth_(column9)) ? (`${":"}${column9??''}`) : (null))??''}`) : (null));
const G__614 = type4;
switch (G__614) {case "pass":
return null;

break;
case "fail":
console.error(`${"FAIL in "}${current_test_str()??''}${((squint_core.truth_(location11)) ? (`${" ("}${location11??''}${")"}`) : (null))??''}`);
if (squint_core.truth_(message5)) {
console.error("  ", message5)};
console.error("  expected:", squint_core.pr_str(expected6));
return console.error("    actual:", squint_core.pr_str(actual7));

break;
case "error":
console.error(`${"ERROR in "}${current_test_str()??''}${((squint_core.truth_(location11)) ? (`${" ("}${location11??''}${")"}`) : (null))??''}`);
if (squint_core.truth_(message5)) {
console.error("  ", message5)};
if (squint_core.truth_(expected6)) {
console.error("  expected:", squint_core.pr_str(expected6))};
return console.error("    actual:", squint_core.pr_str(actual7));

break;
case "begin-test-ns":
return console.log("\nTesting", `${squint_core.get(m3, "ns")??''}`);

break;
case "end-test-ns":
return null;

break;
case "begin-test-var":
return null;

break;
case "end-test-var":
return null;

break;
case "summary":
const map__1617 = squint_core.get(get_current_env(), "report-counters");
const test18 = squint_core.get(map__1617, "test");
const pass19 = squint_core.get(map__1617, "pass");
const fail20 = squint_core.get(map__1617, "fail");
const error21 = squint_core.get(map__1617, "error");
console.log("\nRan", test18, "tests containing", (pass19 + fail20 + error21), "assertions.");
return console.log(`${fail20??''}`, "failures,", `${error21??''}`, "errors.");

break;
default:
return console.log("Unknown report type:", type4, m3)};

};
var successful_QMARK_ = function (results) {
return ((squint_core.get(results, "fail", 0) === 0) && (squint_core.get(results, "error", 0) === 0));

};
var async_QMARK_ = function (x) {
const c__29699__auto__1 = Promise;
const x__29700__auto__2 = x;
const ret__29701__auto__3 = (x__29700__auto__2 instanceof c__29699__auto__1);
return ret__29701__auto__3;

};
var wrap_async = function (setup, teardown) {
return function (test_fn) {
setup();
const result1 = test_fn();
if (squint_core.truth_(async_QMARK_(result1))) {
return result1.finally(teardown)} else {
teardown();
return result1;
};

};

};
var compose_fixtures = function (f1, f2) {
return function (g) {
return f1((function () {
return f2(g);

}));

};

};
var join_fixtures = function (fixtures) {
return squint_core.reduce(compose_fixtures, (function (f) {
return f();

}), fixtures);

};
var get_each_fixtures = (() => {
const f7 = (function (...args8) {
const G__91 = args8.length;
switch (G__91) {case 0:
return f7.cljs$core$IFn$_invoke$arity$0();

break;
case 1:
return f7.cljs$core$IFn$_invoke$arity$1(args8[0]);

break;
default:
throw (new Error(`${"Invalid arity: "}${args8.length??''}`))};

});
f7.cljs$core$IFn$_invoke$arity$0 = (function () {
return get_each_fixtures(null);

});
f7.cljs$core$IFn$_invoke$arity$1 = (function (ns_str) {
return squint_core.get_in(get_current_env(), ["each-fixtures", ns_str], []);

});
f7.cljs$lang$maxFixedArity = 1;
return f7;

})();
var set_each_fixtures_BANG_ = (() => {
const f10 = (function (...args11) {
const G__121 = args11.length;
switch (G__121) {case 1:
return f10.cljs$core$IFn$_invoke$arity$1(args11[0]);

break;
case 2:
return f10.cljs$core$IFn$_invoke$arity$2(args11[0], args11[1]);

break;
default:
throw (new Error(`${"Invalid arity: "}${args11.length??''}`))};

});
f10.cljs$core$IFn$_invoke$arity$1 = (function (fixtures) {
return set_each_fixtures_BANG_(null, fixtures);

});
f10.cljs$core$IFn$_invoke$arity$2 = (function (ns_str, fixtures) {
return update_current_env_BANG_(["each-fixtures", ns_str], squint_core.constantly(fixtures));

});
f10.cljs$lang$maxFixedArity = 2;
return f10;

})();
var get_once_fixtures = (() => {
const f13 = (function (...args14) {
const G__151 = args14.length;
switch (G__151) {case 0:
return f13.cljs$core$IFn$_invoke$arity$0();

break;
case 1:
return f13.cljs$core$IFn$_invoke$arity$1(args14[0]);

break;
default:
throw (new Error(`${"Invalid arity: "}${args14.length??''}`))};

});
f13.cljs$core$IFn$_invoke$arity$0 = (function () {
return get_once_fixtures(null);

});
f13.cljs$core$IFn$_invoke$arity$1 = (function (ns_str) {
return squint_core.get_in(get_current_env(), ["once-fixtures", ns_str], []);

});
f13.cljs$lang$maxFixedArity = 1;
return f13;

})();
var set_once_fixtures_BANG_ = (() => {
const f16 = (function (...args17) {
const G__181 = args17.length;
switch (G__181) {case 1:
return f16.cljs$core$IFn$_invoke$arity$1(args17[0]);

break;
case 2:
return f16.cljs$core$IFn$_invoke$arity$2(args17[0], args17[1]);

break;
default:
throw (new Error(`${"Invalid arity: "}${args17.length??''}`))};

});
f16.cljs$core$IFn$_invoke$arity$1 = (function (fixtures) {
return set_once_fixtures_BANG_(null, fixtures);

});
f16.cljs$core$IFn$_invoke$arity$2 = (function (ns_str, fixtures) {
return update_current_env_BANG_(["once-fixtures", ns_str], squint_core.constantly(fixtures));

});
f16.cljs$lang$maxFixedArity = 2;
return f16;

})();
var test_var = function (v) {
if (squint_core.truth_(squint_core.fn_QMARK_(v))) {
const test_name1 = (() => {
const or__29759__auto__2 = squint_core.get(squint_core.meta(v), "name");
if (squint_core.truth_(or__29759__auto__2)) {
return or__29759__auto__2} else {
return "anonymous"};

})();
const ns_str3 = squint_core.get(squint_core.meta(v), "ns");
const each_fixtures4 = (() => {
const per_ns5 = get_each_fixtures(ns_str3);
if (squint_core.truth_(squint_core.seq(per_ns5))) {
return per_ns5} else {
return get_each_fixtures()};

})();
const wrapped_test6 = ((squint_core.truth_(squint_core.seq(each_fixtures4))) ? ((function () {
return join_fixtures(each_fixtures4)(v);

})) : (v));
const pop_test_name_BANG_7 = (function () {
return update_current_env_BANG_(["testing-vars"], squint_core.rest);

});
update_current_env_BANG_(["testing-vars"], squint_core.conj, test_name1);
inc_report_counter_BANG_("test");
return (() => {
try{
const result8 = wrapped_test6();
if (squint_core.truth_(async_QMARK_(result8))) {
return result8.then((function (r) {
pop_test_name_BANG_7();
return r;

})).catch((function (e) {
report(({"type": "error", "message": e.message, "expected": null, "actual": e}));
return pop_test_name_BANG_7();

}))} else {
pop_test_name_BANG_7();
return result8;
};
}
catch(e9){
pop_test_name_BANG_7();
return report(({"type": "error", "message": e9.message, "expected": null, "actual": e9}));
}

})();
};

};
var test_registry = squint_core.atom(({}));
var register_test_BANG_ = function (ns_str, test_fn) {
const test_name1 = squint_core.get(squint_core.meta(test_fn), "name");
squint_core.swap_BANG_(test_registry, squint_core.assoc_in, [ns_str, test_name1], test_fn);
return test_fn;

};
var registered_tests = (() => {
const f19 = (function (...args20) {
const G__211 = args20.length;
switch (G__211) {case 0:
return f19.cljs$core$IFn$_invoke$arity$0();

break;
case 1:
return f19.cljs$core$IFn$_invoke$arity$1(args20[0]);

break;
default:
throw (new Error(`${"Invalid arity: "}${args20.length??''}`))};

});
f19.cljs$core$IFn$_invoke$arity$0 = (function () {
return squint_core.vec(squint_core.mapcat(squint_core.vals, squint_core.vals(squint_core.deref(test_registry))));

});
f19.cljs$core$IFn$_invoke$arity$1 = (function (ns_str) {
return squint_core.vec(squint_core.vals(squint_core.get(squint_core.deref(test_registry), ns_str)));

});
f19.cljs$lang$maxFixedArity = 1;
return f19;

})();
var run_vars_with_once_fixtures = function (ns_str, vars) {
const per_ns1 = get_once_fixtures(ns_str);
const once_fixtures2 = ((squint_core.truth_(squint_core.seq(per_ns1))) ? (per_ns1) : (get_once_fixtures()));
const run_all3 = (function () {
return squint_core.reduce((function (chain, v) {
if (squint_core.truth_(async_QMARK_(chain))) {
return chain.then((function (_) {
return test_var(v);

}))} else {
return test_var(v)};

}), null, vars);

});
const run_with_fixtures4 = (function () {
if (squint_core.truth_(squint_core.seq(once_fixtures2))) {
return join_fixtures(once_fixtures2)(run_all3)} else {
return run_all3()};

});
if (squint_core.truth_(ns_str)) {
report(({"type": "begin-test-ns", "ns": ns_str}))};
const chain5 = run_with_fixtures4();
const finish6 = (function (r) {
if (squint_core.truth_(ns_str)) {
report(({"type": "end-test-ns", "ns": ns_str}))};
return r;

});
if (squint_core.truth_(async_QMARK_(chain5))) {
return chain5.then(finish6)} else {
return finish6(chain5)};

};
var fresh_counters = ({"test": 0, "pass": 0, "fail": 0, "error": 0});
var run_tests = (() => {
const f22 = (function (var_args) {
const args231 = [];
const len__29484__auto__2 = arguments.length;
let i243 = 0;
while(true){
if ((i243 < len__29484__auto__2)) {
args231.push((arguments[i243]));
let G__4 = (i243 + 1);
i243 = G__4;
continue;
};break;
}
;
const argseq__29668__auto__5 = (((0 < args231.length)) ? (args231.slice(0)) : (null));
return f22.cljs$core$IFn$_invoke$arity$variadic(argseq__29668__auto__5);

});
f22.cljs$core$IFn$_invoke$arity$variadic = (function (args) {
const test_vars6 = ((squint_core.truth_(squint_core.empty_QMARK_(args))) ? (registered_tests()) : (((squint_core.truth_(squint_core.string_QMARK_(squint_core.first(args)))) ? (squint_core.vec(squint_core.mapcat(registered_tests, args))) : ((("else") ? (args) : (null))))));
const _7 = (((_STAR_current_env_STAR_ == null)) ? (set_env_BANG_(empty_env())) : (null));
const saved_counters8 = squint_core.get(get_current_env(), "report-counters");
const _9 = update_current_env_BANG_(["report-counters"], squint_core.constantly(fresh_counters));
const groups10 = squint_core.reduce((function (acc, v) {
const k11 = squint_core.get(squint_core.meta(v), "ns");
return squint_core.update(acc, k11, squint_core.fnil(squint_core.conj, []), v);

}), ({}), test_vars6);
const run_groups12 = (function () {
return squint_core.reduce((function (chain, p__26) {
const vec__1316 = p__26;
const ns_str17 = squint_core.nth(vec__1316, 0, null);
const vars18 = squint_core.nth(vec__1316, 1, null);
if (squint_core.truth_(async_QMARK_(chain))) {
return chain.then((function (_) {
return run_vars_with_once_fixtures(ns_str17, vars18);

}))} else {
return run_vars_with_once_fixtures(ns_str17, vars18)};

}), null, groups10);

});
const finish19 = (function (_) {
report(({"type": "summary"}));
const counters20 = squint_core.get(get_current_env(), "report-counters");
update_current_env_BANG_(["report-counters"], squint_core.constantly(saved_counters8));
return counters20;

});
const chain21 = run_groups12();
if (squint_core.truth_(async_QMARK_(chain21))) {
return chain21.then(finish19)} else {
return finish19(null)};

});
f22.cljs$lang$maxFixedArity = 0;
return f22;

})();

export { _STAR_current_env_STAR_, get_once_fixtures, get_each_fixtures, update_current_env_BANG_, current_test_str, wrap_async, register_test_BANG_, empty_env, testing_contexts_str, async_QMARK_, get_current_env, testing_vars_str, run_tests, set_each_fixtures_BANG_, registered_tests, clear_env_BANG_, set_env_BANG_, report, inc_report_counter_BANG_, test_var, compose_fixtures, successful_QMARK_, set_once_fixtures_BANG_, join_fixtures }
