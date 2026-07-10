import * as squint_core from 'squint-cljs/core.js';
import * as squint_multi from 'squint-cljs/src/squint/multi.js';
import * as clojure_DOT_string from 'squint-cljs/src/squint/string.js';
var _STAR_current_env_STAR_ = ({val: null});
var _STAR_current_reporter_STAR_ = ({val: squint_core.keyword("cljs.test/default")});
var current_reporter = function () {
const or__30450__auto__1 = _STAR_current_reporter_STAR_.val;
if (squint_core.truth_(or__30450__auto__1)) {
return or__30450__auto__1} else {
return squint_core.keyword("cljs.test/default")};

};
var empty_env = function () {
return ({"report-counters": ({"test": 0, "pass": 0, "fail": 0, "error": 0}), "testing-vars": squint_core.list(), "testing-contexts": squint_core.list(), "once-fixtures": ({}), "each-fixtures": ({})});

};
var get_current_env = function () {
const or__30450__auto__1 = _STAR_current_env_STAR_.val;
if (squint_core.truth_(or__30450__auto__1)) {
return or__30450__auto__1} else {
return empty_env()};

};
var set_env_BANG_ = function (env) {
_STAR_current_env_STAR_.val = env;
return env;

};
var clear_env_BANG_ = function () {
_STAR_current_env_STAR_.val = null;
return null;

};
var update_current_env_BANG_ = /* @__PURE__ */ (() => {
const impl51 = (function (ks, f, args) {
const env2 = get_current_env();
const new_env3 = squint_core.apply(squint_core.update_in, env2, ks, f, args);
return set_env_BANG_(new_env3);

});
const f1 = (function (arg2, arg3, ...rest4) {
const self__30970__auto__4 = this;
return impl51.call(self__30970__auto__4, arg2, arg3, (((rest4.length === 0)) ? (null) : (rest4)));

});
(f1["squint$lang$variadic"] = impl51);
return f1;

})();
var testing_contexts_str = function () {
const temp__29967__auto__1 = squint_core.seq(squint_core.get(get_current_env(), squint_core.keyword("testing-contexts")));
if (squint_core.truth_(temp__29967__auto__1)) {
const contexts2 = temp__29967__auto__1;
return clojure_DOT_string.join(" ", squint_core.reverse(contexts2));
};

};
var testing_vars_str = function () {
const temp__29967__auto__1 = squint_core.seq(squint_core.get(get_current_env(), squint_core.keyword("testing-vars")));
if (squint_core.truth_(temp__29967__auto__1)) {
const vars2 = temp__29967__auto__1;
return clojure_DOT_string.join(" ", squint_core.map(squint_core.str, vars2));
};

};
var inc_report_counter_BANG_ = function (name) {
if (squint_core.truth_(squint_core.get(get_current_env(), squint_core.keyword("report-counters")))) {
return update_current_env_BANG_([squint_core.keyword("report-counters"), name], squint_core.fnil(squint_core.inc, 0));
};

};
var current_test_str = function () {
const vars1 = testing_vars_str();
const ctx2 = testing_contexts_str();
if (squint_core.truth_((() => {
const and__30488__auto__3 = vars1;
if (squint_core.truth_(and__30488__auto__3)) {
return ctx2} else {
return and__30488__auto__3};

})())) {
return `${vars1??''}${" "}${ctx2??''}`} else {
if (squint_core.truth_(vars1)) {
return vars1} else {
if (squint_core.truth_(ctx2)) {
return ctx2} else {
if (squint_core.keyword("else")) {
return "test"} else {
return null}}}};

};
var report_loc = function (p__6) {
const map__12 = p__6;
const line3 = squint_core.get(map__12, "line");
const column4 = squint_core.get(map__12, "column");
const file5 = squint_core.get(map__12, "file");
if (squint_core.truth_((() => {
const or__30450__auto__6 = line3;
if (squint_core.truth_(or__30450__auto__6)) {
return or__30450__auto__6} else {
const or__30450__auto__7 = column4;
if (squint_core.truth_(or__30450__auto__7)) {
return or__30450__auto__7} else {
return file5};
};

})())) {
return `${((squint_core.truth_(file5)) ? (`${file5??''}${":"}`) : (null))??''}${((squint_core.truth_(line3)) ? (line3) : (null))??''}${((squint_core.truth_(column4)) ? (`${":"}${column4??''}`) : (null))??''}`;
};

};
var report = squint_multi.defmulti("report", (function (m) {
return [current_reporter(), squint_core.get(m, squint_core.keyword("type"))];

}), ({}));
squint_multi.defmethod(report, squint_core.keyword("default"), (function (_m) {
return null;

}));
squint_multi.defmethod(report, [squint_core.keyword("cljs.test/default"), squint_core.keyword("pass")], (function (_) {
return inc_report_counter_BANG_(squint_core.keyword("pass"));

}));
squint_multi.defmethod(report, [squint_core.keyword("cljs.test/default"), squint_core.keyword("fail")], (function (m) {
inc_report_counter_BANG_(squint_core.keyword("fail"));
squint_core.println(`${"FAIL in "}${current_test_str()??''}${(() => {
const temp__29967__auto__1 = report_loc(m);
if (squint_core.truth_(temp__29967__auto__1)) {
const l2 = temp__29967__auto__1;
return `${" ("}${l2??''}${")"}`;
};

})()??''}`);
if (squint_core.truth_(squint_core.get(m, squint_core.keyword("message")))) {
squint_core.println("  ", squint_core.get(m, squint_core.keyword("message")))};
squint_core.println("  expected:", squint_core.pr_str(squint_core.get(m, squint_core.keyword("expected"))));
return squint_core.println("    actual:", squint_core.pr_str(squint_core.get(m, squint_core.keyword("actual"))));

}));
squint_multi.defmethod(report, [squint_core.keyword("cljs.test/default"), squint_core.keyword("error")], (function (m) {
inc_report_counter_BANG_(squint_core.keyword("error"));
squint_core.println(`${"ERROR in "}${current_test_str()??''}${(() => {
const temp__29967__auto__1 = report_loc(m);
if (squint_core.truth_(temp__29967__auto__1)) {
const l2 = temp__29967__auto__1;
return `${" ("}${l2??''}${")"}`;
};

})()??''}`);
if (squint_core.truth_(squint_core.get(m, squint_core.keyword("message")))) {
squint_core.println("  ", squint_core.get(m, squint_core.keyword("message")))};
if (squint_core.truth_(squint_core.get(m, squint_core.keyword("expected")))) {
squint_core.println("  expected:", squint_core.pr_str(squint_core.get(m, squint_core.keyword("expected"))))};
return squint_core.println("    actual:", squint_core.pr_str(squint_core.get(m, squint_core.keyword("actual"))));

}));
squint_multi.defmethod(report, [squint_core.keyword("cljs.test/default"), squint_core.keyword("begin-test-ns")], (function (m) {
return squint_core.println("\nTesting", `${squint_core.get(m, squint_core.keyword("ns"))??''}`);

}));
squint_multi.defmethod(report, [squint_core.keyword("cljs.test/default"), squint_core.keyword("end-test-ns")], (function (_) {
return null;

}));
squint_multi.defmethod(report, [squint_core.keyword("cljs.test/default"), squint_core.keyword("begin-test-var")], (function (_) {
return null;

}));
squint_multi.defmethod(report, [squint_core.keyword("cljs.test/default"), squint_core.keyword("end-test-var")], (function (_) {
return null;

}));
squint_multi.defmethod(report, [squint_core.keyword("cljs.test/default"), squint_core.keyword("summary")], (function (_) {
let map__17 = squint_core.get(get_current_env(), squint_core.keyword("report-counters"));
let test8 = squint_core.get(map__17, "test");
let pass9 = squint_core.get(map__17, "pass");
let fail10 = squint_core.get(map__17, "fail");
let error11 = squint_core.get(map__17, "error");
squint_core.println("\nRan", test8, "tests containing", (pass9 + fail10 + error11), "assertions.");
return squint_core.println(`${fail10??''}`, "failures,", `${error11??''}`, "errors.");

}));
var successful_QMARK_ = function (results) {
return ((squint_core.get(results, squint_core.keyword("fail"), 0) === 0) && (squint_core.get(results, squint_core.keyword("error"), 0) === 0));

};
var async_QMARK_ = function (x) {
const c__30362__auto__1 = Promise;
const x__30363__auto__2 = x;
const ret__30364__auto__3 = (x__30363__auto__2 instanceof c__30362__auto__1);
return ret__30364__auto__3;

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
var get_each_fixtures = /* @__PURE__ */ (() => {
const impl151 = (function () {
return get_each_fixtures(null);

});
const impl162 = (function (ns_str) {
return squint_core.get_in(get_current_env(), [squint_core.keyword("each-fixtures"), ns_str], []);

});
const f12 = (function (...args13) {
const self173 = this;
const G__184 = args13.length;
switch (G__184) {case 0:
return impl151.call(self173);

break;
case 1:
return impl162.call(self173, args13[0]);

break;
default:
throw (new Error(`${"Invalid arity: "}${args13.length??''}`))};

});
return f12;

})();
var set_each_fixtures_BANG_ = /* @__PURE__ */ (() => {
const impl221 = (function (fixtures) {
return set_each_fixtures_BANG_(null, fixtures);

});
const impl232 = (function (ns_str, fixtures) {
return update_current_env_BANG_([squint_core.keyword("each-fixtures"), ns_str], squint_core.constantly(fixtures));

});
const f19 = (function (...args20) {
const self243 = this;
const G__254 = args20.length;
switch (G__254) {case 1:
return impl221.call(self243, args20[0]);

break;
case 2:
return impl232.call(self243, args20[0], args20[1]);

break;
default:
throw (new Error(`${"Invalid arity: "}${args20.length??''}`))};

});
return f19;

})();
var get_once_fixtures = /* @__PURE__ */ (() => {
const impl291 = (function () {
return get_once_fixtures(null);

});
const impl302 = (function (ns_str) {
return squint_core.get_in(get_current_env(), [squint_core.keyword("once-fixtures"), ns_str], []);

});
const f26 = (function (...args27) {
const self313 = this;
const G__324 = args27.length;
switch (G__324) {case 0:
return impl291.call(self313);

break;
case 1:
return impl302.call(self313, args27[0]);

break;
default:
throw (new Error(`${"Invalid arity: "}${args27.length??''}`))};

});
return f26;

})();
var set_once_fixtures_BANG_ = /* @__PURE__ */ (() => {
const impl361 = (function (fixtures) {
return set_once_fixtures_BANG_(null, fixtures);

});
const impl372 = (function (ns_str, fixtures) {
return update_current_env_BANG_([squint_core.keyword("once-fixtures"), ns_str], squint_core.constantly(fixtures));

});
const f33 = (function (...args34) {
const self383 = this;
const G__394 = args34.length;
switch (G__394) {case 1:
return impl361.call(self383, args34[0]);

break;
case 2:
return impl372.call(self383, args34[0], args34[1]);

break;
default:
throw (new Error(`${"Invalid arity: "}${args34.length??''}`))};

});
return f33;

})();
var test_var = function (v) {
if (squint_core.truth_(squint_core.fn_QMARK_(v))) {
const test_name1 = (() => {
const or__30450__auto__2 = squint_core.get(squint_core.meta(v), squint_core.keyword("name"));
if (squint_core.truth_(or__30450__auto__2)) {
return or__30450__auto__2} else {
return "anonymous"};

})();
const ns_str3 = squint_core.get(squint_core.meta(v), squint_core.keyword("ns"));
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
report(({"type": squint_core.keyword("end-test-var"), "name": test_name1, "ns": ns_str3, "var": v}));
return update_current_env_BANG_([squint_core.keyword("testing-vars")], squint_core.rest);

});
update_current_env_BANG_([squint_core.keyword("testing-vars")], squint_core.conj, test_name1);
inc_report_counter_BANG_(squint_core.keyword("test"));
report(({"type": squint_core.keyword("begin-test-var"), "name": test_name1, "ns": ns_str3, "var": v}));
return (() => {
try{
const result8 = wrapped_test6();
if (squint_core.truth_(async_QMARK_(result8))) {
return result8.then((function (r) {
end_BANG_7();
return r;

})).catch((function (e) {
report(({"type": squint_core.keyword("error"), "message": e.message, "expected": null, "actual": e}));
return end_BANG_7();

}))} else {
end_BANG_7();
return result8;
};
}
catch(e9){
report(({"type": squint_core.keyword("error"), "message": e9.message, "expected": null, "actual": e9}));
return end_BANG_7();
}

})();
};

};
var test_registry = squint_core.atom(({}));
var register_test_BANG_ = function (ns_str, test_fn) {
const test_name1 = squint_core.get(squint_core.meta(test_fn), squint_core.keyword("name"));
squint_core.swap_BANG_(test_registry, squint_core.assoc_in, [ns_str, test_name1], test_fn);
return test_fn;

};
var registered_tests = /* @__PURE__ */ (() => {
const impl431 = (function () {
return squint_core.vec(squint_core.mapcat(squint_core.vals, squint_core.vals(squint_core.deref(test_registry))));

});
const impl442 = (function (ns_str) {
return squint_core.vec(squint_core.vals(squint_core.get(squint_core.deref(test_registry), ns_str)));

});
const f40 = (function (...args41) {
const self453 = this;
const G__464 = args41.length;
switch (G__464) {case 0:
return impl431.call(self453);

break;
case 1:
return impl442.call(self453, args41[0]);

break;
default:
throw (new Error(`${"Invalid arity: "}${args41.length??''}`))};

});
return f40;

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
report(({"type": squint_core.keyword("begin-test-ns"), "ns": ns_str}))};
const chain5 = run_with_fixtures4();
const finish6 = (function (r) {
if (squint_core.truth_(ns_str)) {
report(({"type": squint_core.keyword("end-test-ns"), "ns": ns_str}))};
return r;

});
if (squint_core.truth_(async_QMARK_(chain5))) {
return chain5.then(finish6)} else {
return finish6(chain5)};

};
var fresh_counters = ({"test": 0, "pass": 0, "fail": 0, "error": 0});
var run_tests = /* @__PURE__ */ (() => {
const impl491 = (function (args) {
const test_vars2 = ((squint_core.truth_(squint_core.empty_QMARK_(args))) ? (registered_tests()) : (((squint_core.truth_(squint_core.string_QMARK_(squint_core.first(args)))) ? (squint_core.vec(squint_core.mapcat(registered_tests, args))) : (((squint_core.keyword("else")) ? (args) : (null))))));
const _3 = (((_STAR_current_env_STAR_.val == null)) ? (set_env_BANG_(empty_env())) : (null));
const saved_counters4 = squint_core.get(get_current_env(), squint_core.keyword("report-counters"));
const _5 = update_current_env_BANG_([squint_core.keyword("report-counters")], squint_core.constantly(fresh_counters));
const groups6 = squint_core.reduce((function (acc, v) {
const k7 = squint_core.get(squint_core.meta(v), squint_core.keyword("ns"));
return squint_core.update(acc, k7, squint_core.fnil(squint_core.conj, []), v);

}), ({}), test_vars2);
const run_groups8 = (function () {
return squint_core.reduce((function (chain, p__50) {
const vec__912 = p__50;
const ns_str13 = squint_core.nth(vec__912, 0, null);
const vars14 = squint_core.nth(vec__912, 1, null);
if (squint_core.truth_(async_QMARK_(chain))) {
return chain.then((function (_) {
return run_vars_with_once_fixtures(ns_str13, vars14);

}))} else {
return run_vars_with_once_fixtures(ns_str13, vars14)};

}), null, groups6);

});
const finish15 = (function (_) {
report(({"type": squint_core.keyword("summary")}));
const counters16 = squint_core.get(get_current_env(), squint_core.keyword("report-counters"));
update_current_env_BANG_([squint_core.keyword("report-counters")], squint_core.constantly(saved_counters4));
return counters16;

});
const chain17 = run_groups8();
if (squint_core.truth_(async_QMARK_(chain17))) {
return chain17.then(finish15)} else {
return finish15(null)};

});
const f47 = (function (...rest48) {
const self__30970__auto__18 = this;
return impl491.call(self__30970__auto__18, (((rest48.length === 0)) ? (null) : (rest48)));

});
(f47["squint$lang$variadic"] = impl491);
return f47;

})();

export { _STAR_current_env_STAR_, get_once_fixtures, get_each_fixtures, update_current_env_BANG_, current_test_str, wrap_async, register_test_BANG_, empty_env, testing_contexts_str, async_QMARK_, get_current_env, testing_vars_str, run_tests, set_each_fixtures_BANG_, registered_tests, clear_env_BANG_, _STAR_current_reporter_STAR_, set_env_BANG_, current_reporter, report, inc_report_counter_BANG_, test_var, compose_fixtures, successful_QMARK_, set_once_fixtures_BANG_, join_fixtures }
