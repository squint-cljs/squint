import * as squint_core from 'squint-cljs/core.js';
import * as squint_multi from 'squint-cljs/src/squint/multi.js';
import * as clojure_DOT_string from 'squint-cljs/src/squint/string.js';
var _STAR_current_env_STAR_ = null;
var _STAR_current_reporter_STAR_ = "cljs.test/default";
var current_reporter = function () {
const or__28351__auto__1 = _STAR_current_reporter_STAR_;
if (squint_core.truth_(or__28351__auto__1)) {
return or__28351__auto__1} else {
return "cljs.test/default"};

};
var empty_env = function () {
return ({"report-counters": ({"test": 0, "pass": 0, "fail": 0, "error": 0}), "testing-vars": squint_core.list(), "testing-contexts": squint_core.list(), "once-fixtures": ({}), "each-fixtures": ({})});

};
var get_current_env = function () {
const or__28351__auto__1 = _STAR_current_env_STAR_;
if (squint_core.truth_(or__28351__auto__1)) {
return or__28351__auto__1} else {
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
const len__27315__auto__2 = arguments.length;
let i33 = 0;
while(true){
if ((i33 < len__27315__auto__2)) {
args21.push((arguments[i33]));
let G__4 = (i33 + 1);
i33 = G__4;
continue;
};break;
}
;
const argseq__27755__auto__5 = (((2 < args21.length)) ? (args21.slice(2)) : (null));
return f1.cljs$core$IFn$_invoke$arity$variadic((arguments[0]), (arguments[1]), argseq__27755__auto__5);

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
const temp__27795__auto__1 = squint_core.seq(squint_core.get(get_current_env(), "testing-contexts"));
if (squint_core.truth_(temp__27795__auto__1)) {
const contexts2 = temp__27795__auto__1;
return clojure_DOT_string.join(" ", squint_core.reverse(contexts2));
};

};
var testing_vars_str = function () {
const temp__27795__auto__1 = squint_core.seq(squint_core.get(get_current_env(), "testing-vars"));
if (squint_core.truth_(temp__27795__auto__1)) {
const vars2 = temp__27795__auto__1;
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
const and__28377__auto__3 = vars1;
if (squint_core.truth_(and__28377__auto__3)) {
return ctx2} else {
return and__28377__auto__3};

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
var report_loc = function (p__5) {
const map__12 = p__5;
const line3 = squint_core.get(map__12, "line");
const column4 = squint_core.get(map__12, "column");
const file5 = squint_core.get(map__12, "file");
if (squint_core.truth_((() => {
const or__28351__auto__6 = line3;
if (squint_core.truth_(or__28351__auto__6)) {
return or__28351__auto__6} else {
const or__28351__auto__7 = column4;
if (squint_core.truth_(or__28351__auto__7)) {
return or__28351__auto__7} else {
return file5};
};

})())) {
return `${((squint_core.truth_(file5)) ? (`${file5??''}${":"}`) : (null))??''}${((squint_core.truth_(line3)) ? (line3) : (null))??''}${((squint_core.truth_(column4)) ? (`${":"}${column4??''}`) : (null))??''}`;
};

};
var report = squint_multi.defmulti("report", (function (m) {
return [current_reporter(), squint_core.get(m, "type")];

}), ({}));
squint_multi.defmethod(report, "default", (function (_m) {
return null;

}));
squint_multi.defmethod(report, ["cljs.test/default", "pass"], (function (_) {
return inc_report_counter_BANG_("pass");

}));
squint_multi.defmethod(report, ["cljs.test/default", "fail"], (function (m) {
inc_report_counter_BANG_("fail");
console.error(`${"FAIL in "}${current_test_str()??''}${(() => {
const temp__27795__auto__1 = report_loc(m);
if (squint_core.truth_(temp__27795__auto__1)) {
const l2 = temp__27795__auto__1;
return `${" ("}${l2??''}${")"}`;
};

})()??''}`);
if (squint_core.truth_(squint_core.get(m, "message"))) {
console.error("  ", squint_core.get(m, "message"))};
console.error("  expected:", squint_core.pr_str(squint_core.get(m, "expected")));
return console.error("    actual:", squint_core.pr_str(squint_core.get(m, "actual")));

}));
squint_multi.defmethod(report, ["cljs.test/default", "error"], (function (m) {
inc_report_counter_BANG_("error");
console.error(`${"ERROR in "}${current_test_str()??''}${(() => {
const temp__27795__auto__1 = report_loc(m);
if (squint_core.truth_(temp__27795__auto__1)) {
const l2 = temp__27795__auto__1;
return `${" ("}${l2??''}${")"}`;
};

})()??''}`);
if (squint_core.truth_(squint_core.get(m, "message"))) {
console.error("  ", squint_core.get(m, "message"))};
if (squint_core.truth_(squint_core.get(m, "expected"))) {
console.error("  expected:", squint_core.pr_str(squint_core.get(m, "expected")))};
return console.error("    actual:", squint_core.pr_str(squint_core.get(m, "actual")));

}));
squint_multi.defmethod(report, ["cljs.test/default", "begin-test-ns"], (function (m) {
return console.log("\nTesting", `${squint_core.get(m, "ns")??''}`);

}));
squint_multi.defmethod(report, ["cljs.test/default", "end-test-ns"], (function (_) {
return null;

}));
squint_multi.defmethod(report, ["cljs.test/default", "begin-test-var"], (function (_) {
return null;

}));
squint_multi.defmethod(report, ["cljs.test/default", "end-test-var"], (function (_) {
return null;

}));
squint_multi.defmethod(report, ["cljs.test/default", "summary"], (function (_) {
let map__16 = squint_core.get(get_current_env(), "report-counters");
let test7 = squint_core.get(map__16, "test");
let pass8 = squint_core.get(map__16, "pass");
let fail9 = squint_core.get(map__16, "fail");
let error10 = squint_core.get(map__16, "error");
console.log("\nRan", test7, "tests containing", (pass8 + fail9 + error10), "assertions.");
return console.log(`${fail9??''}`, "failures,", `${error10??''}`, "errors.");

}));
var successful_QMARK_ = function (results) {
return ((squint_core.get(results, "fail", 0) === 0) && (squint_core.get(results, "error", 0) === 0));

};
var async_QMARK_ = function (x) {
const c__28311__auto__1 = Promise;
const x__28312__auto__2 = x;
const ret__28313__auto__3 = (x__28312__auto__2 instanceof c__28311__auto__1);
return ret__28313__auto__3;

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
const f11 = (function (...args12) {
const G__131 = args12.length;
switch (G__131) {case 0:
return f11.cljs$core$IFn$_invoke$arity$0();

break;
case 1:
return f11.cljs$core$IFn$_invoke$arity$1(args12[0]);

break;
default:
throw (new Error(`${"Invalid arity: "}${args12.length??''}`))};

});
f11.cljs$core$IFn$_invoke$arity$0 = (function () {
return get_each_fixtures(null);

});
f11.cljs$core$IFn$_invoke$arity$1 = (function (ns_str) {
return squint_core.get_in(get_current_env(), ["each-fixtures", ns_str], []);

});
f11.cljs$lang$maxFixedArity = 1;
return f11;

})();
var set_each_fixtures_BANG_ = (() => {
const f14 = (function (...args15) {
const G__161 = args15.length;
switch (G__161) {case 1:
return f14.cljs$core$IFn$_invoke$arity$1(args15[0]);

break;
case 2:
return f14.cljs$core$IFn$_invoke$arity$2(args15[0], args15[1]);

break;
default:
throw (new Error(`${"Invalid arity: "}${args15.length??''}`))};

});
f14.cljs$core$IFn$_invoke$arity$1 = (function (fixtures) {
return set_each_fixtures_BANG_(null, fixtures);

});
f14.cljs$core$IFn$_invoke$arity$2 = (function (ns_str, fixtures) {
return update_current_env_BANG_(["each-fixtures", ns_str], squint_core.constantly(fixtures));

});
f14.cljs$lang$maxFixedArity = 2;
return f14;

})();
var get_once_fixtures = (() => {
const f17 = (function (...args18) {
const G__191 = args18.length;
switch (G__191) {case 0:
return f17.cljs$core$IFn$_invoke$arity$0();

break;
case 1:
return f17.cljs$core$IFn$_invoke$arity$1(args18[0]);

break;
default:
throw (new Error(`${"Invalid arity: "}${args18.length??''}`))};

});
f17.cljs$core$IFn$_invoke$arity$0 = (function () {
return get_once_fixtures(null);

});
f17.cljs$core$IFn$_invoke$arity$1 = (function (ns_str) {
return squint_core.get_in(get_current_env(), ["once-fixtures", ns_str], []);

});
f17.cljs$lang$maxFixedArity = 1;
return f17;

})();
var set_once_fixtures_BANG_ = (() => {
const f20 = (function (...args21) {
const G__221 = args21.length;
switch (G__221) {case 1:
return f20.cljs$core$IFn$_invoke$arity$1(args21[0]);

break;
case 2:
return f20.cljs$core$IFn$_invoke$arity$2(args21[0], args21[1]);

break;
default:
throw (new Error(`${"Invalid arity: "}${args21.length??''}`))};

});
f20.cljs$core$IFn$_invoke$arity$1 = (function (fixtures) {
return set_once_fixtures_BANG_(null, fixtures);

});
f20.cljs$core$IFn$_invoke$arity$2 = (function (ns_str, fixtures) {
return update_current_env_BANG_(["once-fixtures", ns_str], squint_core.constantly(fixtures));

});
f20.cljs$lang$maxFixedArity = 2;
return f20;

})();
var test_var = function (v) {
if (squint_core.truth_(squint_core.fn_QMARK_(v))) {
const test_name1 = (() => {
const or__28351__auto__2 = squint_core.get(squint_core.meta(v), "name");
if (squint_core.truth_(or__28351__auto__2)) {
return or__28351__auto__2} else {
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
const end_BANG_7 = (function () {
report(({"type": "end-test-var", "name": test_name1, "ns": ns_str3, "var": v}));
return update_current_env_BANG_(["testing-vars"], squint_core.rest);

});
update_current_env_BANG_(["testing-vars"], squint_core.conj, test_name1);
inc_report_counter_BANG_("test");
report(({"type": "begin-test-var", "name": test_name1, "ns": ns_str3, "var": v}));
return (() => {
try{
const result8 = wrapped_test6();
if (squint_core.truth_(async_QMARK_(result8))) {
return result8.then((function (r) {
end_BANG_7();
return r;

})).catch((function (e) {
report(({"type": "error", "message": e.message, "expected": null, "actual": e}));
return end_BANG_7();

}))} else {
end_BANG_7();
return result8;
};
}
catch(e9){
report(({"type": "error", "message": e9.message, "expected": null, "actual": e9}));
return end_BANG_7();
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
const f23 = (function (...args24) {
const G__251 = args24.length;
switch (G__251) {case 0:
return f23.cljs$core$IFn$_invoke$arity$0();

break;
case 1:
return f23.cljs$core$IFn$_invoke$arity$1(args24[0]);

break;
default:
throw (new Error(`${"Invalid arity: "}${args24.length??''}`))};

});
f23.cljs$core$IFn$_invoke$arity$0 = (function () {
return squint_core.vec(squint_core.mapcat(squint_core.vals, squint_core.vals(squint_core.deref(test_registry))));

});
f23.cljs$core$IFn$_invoke$arity$1 = (function (ns_str) {
return squint_core.vec(squint_core.vals(squint_core.get(squint_core.deref(test_registry), ns_str)));

});
f23.cljs$lang$maxFixedArity = 1;
return f23;

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
const f26 = (function (var_args) {
const args271 = [];
const len__27315__auto__2 = arguments.length;
let i283 = 0;
while(true){
if ((i283 < len__27315__auto__2)) {
args271.push((arguments[i283]));
let G__4 = (i283 + 1);
i283 = G__4;
continue;
};break;
}
;
const argseq__27755__auto__5 = (((0 < args271.length)) ? (args271.slice(0)) : (null));
return f26.cljs$core$IFn$_invoke$arity$variadic(argseq__27755__auto__5);

});
f26.cljs$core$IFn$_invoke$arity$variadic = (function (args) {
const test_vars6 = ((squint_core.truth_(squint_core.empty_QMARK_(args))) ? (registered_tests()) : (((squint_core.truth_(squint_core.string_QMARK_(squint_core.first(args)))) ? (squint_core.vec(squint_core.mapcat(registered_tests, args))) : ((("else") ? (args) : (null))))));
const _7 = (((_STAR_current_env_STAR_ == null)) ? (set_env_BANG_(empty_env())) : (null));
const saved_counters8 = squint_core.get(get_current_env(), "report-counters");
const _9 = update_current_env_BANG_(["report-counters"], squint_core.constantly(fresh_counters));
const groups10 = squint_core.reduce((function (acc, v) {
const k11 = squint_core.get(squint_core.meta(v), "ns");
return squint_core.update(acc, k11, squint_core.fnil(squint_core.conj, []), v);

}), ({}), test_vars6);
const run_groups12 = (function () {
return squint_core.reduce((function (chain, p__30) {
const vec__1316 = p__30;
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
f26.cljs$lang$maxFixedArity = 0;
return f26;

})();

export { _STAR_current_env_STAR_, get_once_fixtures, get_each_fixtures, update_current_env_BANG_, current_test_str, wrap_async, register_test_BANG_, empty_env, testing_contexts_str, async_QMARK_, get_current_env, testing_vars_str, run_tests, set_each_fixtures_BANG_, registered_tests, clear_env_BANG_, _STAR_current_reporter_STAR_, set_env_BANG_, current_reporter, report, inc_report_counter_BANG_, test_var, compose_fixtures, successful_QMARK_, set_once_fixtures_BANG_, join_fixtures }
