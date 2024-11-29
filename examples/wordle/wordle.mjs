import * as squint_core from 'squint-cljs/core.js';
var board_state = squint_core.atom([]);
var counter = squint_core.atom(0);
var attempt = squint_core.atom(0);
var word_of_the_day = squint_core.atom("hello");
var write_letter = function (cell, letter) {
return cell.textContent = letter;
;
};
var make_cell = function () {
const cell1 = document.createElement("div");
cell1.className = "cell";
return cell1;
};
var make_board = function (n) {
const board1 = document.createElement("div");
board1.className = "board";
for (let G__2 of squint_core.iterable(squint_core.range(n))) {
const _3 = G__2;
const cell4 = make_cell();
squint_core.swap_BANG_(board_state, squint_core.conj, cell4);
board1.appendChild(cell4)
};
return board1;
};
var get_letter = function (cell) {
return cell.textContent;
};
var color_cell = function (idx, cell) {
const color1 = (function (el, color) {
return el.style.backgroundColor = color;
;
});
const letter2 = get_letter(cell);
if ((letter2) === (squint_core.nth(squint_core.deref(word_of_the_day), idx))) {
return color1(cell, "green");} else {
if (squint_core.truth_(squint_core.contains_QMARK_(squint_core.set(squint_core.deref(word_of_the_day)), letter2))) {
return color1(cell, "aqua");} else {
if ("else") {
return color1(cell, "#333333");} else {
return null;}}}
};
var check_solution = function (cells) {
for (let G__1 of squint_core.iterable(squint_core.map_indexed(squint_core.vector, cells))) {
const vec__25 = G__1;
const idx6 = squint_core.nth(vec__25, 0, null);
const cell7 = squint_core.nth(vec__25, 1, null);
color_cell(idx6, cell7)
};
return (squint_core.apply(squint_core.str, squint_core.mapv(get_letter, cells))) === (squint_core.str(squint_core.deref(word_of_the_day)));
};
var user_input = function (key) {
const start1 = (5) * (squint_core.deref(attempt));
const end2 = (5) * ((squint_core.deref(attempt) + 1));
if (squint_core.truth_((() => {
const and__24178__auto__3 = squint_core.re_matches(/[a-z]/, key);
if (squint_core.truth_(and__24178__auto__3)) {
return (squint_core.deref(counter)) < (end2);} else {
return and__24178__auto__3;}
})())) {
write_letter(squint_core.nth(squint_core.deref(board_state), squint_core.deref(counter)), key);
return squint_core.swap_BANG_(counter, squint_core.inc);} else {
if (squint_core.truth_((() => {
const and__24178__auto__4 = (key) === ("backspace");
if (and__24178__auto__4) {
return (squint_core.deref(counter)) > (start1);} else {
return and__24178__auto__4;}
})())) {
squint_core.swap_BANG_(counter, squint_core.dec);
return write_letter(squint_core.nth(squint_core.deref(board_state), squint_core.deref(counter)), "");} else {
if (squint_core.truth_((() => {
const and__24178__auto__5 = (key) === ("enter");
if (and__24178__auto__5) {
return (squint_core.deref(counter)) === (end2);} else {
return and__24178__auto__5;}
})())) {
if (squint_core.truth_(check_solution(squint_core.subvec(squint_core.deref(board_state), start1, end2)))) {
alert("You won")};
return squint_core.swap_BANG_(attempt, squint_core.inc);} else {
return null;}}}
};
var listener = squint_core.atom(null);
var unmount = function () {
document.removeEventListener("keydown", squint_core.deref(listener));
const app1 = document.getElementById("app");
return app1.innerHTML = "";
;
};
var mount = function () {
const app1 = document.getElementById("app");
const board2 = make_board(30);
const input_listener3 = (function (e) {
return user_input(e.key.toLowerCase());
});
app1.appendChild(board2);
squint_core.reset_BANG_(listener, input_listener3);
return document.addEventListener("keydown", input_listener3);
};
mount();

export { make_cell, counter, word_of_the_day, board_state, listener, user_input, check_solution, color_cell, attempt, unmount, mount, make_board, write_letter, get_letter }
