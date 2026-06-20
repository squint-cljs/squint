import * as squint_core from 'squint-cljs/core.js';
var walk = function (inner, outer, form) {
if (squint_core.truth_(squint_core.list_QMARK_(form))) {
return outer(squint_core.with_meta(squint_core.apply(squint_core.list, squint_core.map(inner, form)), squint_core.meta(form)))} else {
if (squint_core.truth_(squint_core.map_QMARK_(form))) {
return outer(squint_core.into(squint_core.empty(form), squint_core.map(inner, form)))} else {
if (squint_core.truth_(squint_core.vector_QMARK_(form))) {
return outer(squint_core.into(squint_core.empty(form), squint_core.map(inner, form)))} else {
if (squint_core.truth_(squint_core.set_QMARK_(form))) {
return outer(squint_core.into(squint_core.empty(form), squint_core.map(inner, form)))} else {
if (squint_core.truth_((() => {
const and__28252__auto__1 = squint_core.seq_QMARK_(form);
if (squint_core.truth_(and__28252__auto__1)) {
return squint_core.not(squint_core.string_QMARK_(form))} else {
return and__28252__auto__1};

})())) {
return outer(squint_core.with_meta(squint_core.vec(squint_core.map(inner, form)), squint_core.meta(form)))} else {
if ("else") {
return outer(form)} else {
return null}}}}}};

};
var postwalk = function (f, form) {
return walk((function (x) {
return postwalk(f, x);

}), f, form);

};
var prewalk = function (f, form) {
return walk((function (x) {
return prewalk(f, x);

}), squint_core.identity, f(form));

};
var postwalk_replace = function (smap, form) {
return postwalk((function (x) {
if (squint_core.truth_(squint_core.contains_QMARK_(smap, x))) {
return squint_core.get(smap, x)} else {
return x};

}), form);

};
var prewalk_replace = function (smap, form) {
return prewalk((function (x) {
if (squint_core.truth_(squint_core.contains_QMARK_(smap, x))) {
return squint_core.get(smap, x)} else {
return x};

}), form);

};
var postwalk_demo = function (form) {
return postwalk((function (x) {
squint_core.println("Walked:", squint_core.pr_str(x));
return x;

}), form);

};
var prewalk_demo = function (form) {
return prewalk((function (x) {
squint_core.println("Walked:", squint_core.pr_str(x));
return x;

}), form);

};
var keywordize_keys = function (m) {
return postwalk((function (x) {
if (squint_core.truth_(squint_core.map_QMARK_(x))) {
return squint_core.into(squint_core.empty(x), squint_core.map((function (p__1) {
const vec__14 = p__1;
const k5 = squint_core.nth(vec__14, 0, null);
const v6 = squint_core.nth(vec__14, 1, null);
return [squint_core.name(k5), v6];

}), x))} else {
return x};

}), m);

};
var stringify_keys = function (m) {
return postwalk((function (x) {
if (squint_core.truth_(squint_core.map_QMARK_(x))) {
return squint_core.into(squint_core.empty(x), squint_core.map((function (p__2) {
const vec__14 = p__2;
const k5 = squint_core.nth(vec__14, 0, null);
const v6 = squint_core.nth(vec__14, 1, null);
return [squint_core.name(k5), v6];

}), x))} else {
return x};

}), m);

};

export { stringify_keys, postwalk_demo, postwalk, walk, postwalk_replace, prewalk, keywordize_keys, prewalk_demo, prewalk_replace }
