var squint_core = await import('squint-cljs/core.js');
globalThis.tetris = globalThis.tetris || {};
var reagami = await import('https://esm.sh/reagami@0.0.36');
globalThis.tetris.reagami = reagami;
var COLS = 10;
globalThis.tetris.COLS = COLS;
var ROWS = 20;
globalThis.tetris.ROWS = ROWS;
var TICK_MS = 500;
globalThis.tetris.TICK_MS = TICK_MS;
var COLORS = ({"I": "#22d3ee", "O": "#facc15", "T": "#a855f7", "S": "#22c55e", "Z": "#ef4444", "L": "#f97316", "J": "#3b82f6"});
globalThis.tetris.COLORS = COLORS;
var PIECES = ({"I": [[-1, 0], [0, 0], [1, 0], [2, 0]], "O": [[0, 0], [1, 0], [0, 1], [1, 1]], "T": [[-1, 0], [0, 0], [1, 0], [0, 1]], "S": [[0, 0], [1, 0], [-1, 1], [0, 1]], "Z": [[-1, 0], [0, 0], [0, 1], [1, 1]], "L": [[-1, 0], [0, 0], [1, 0], [-1, 1]], "J": [[-1, 0], [0, 0], [1, 0], [1, 1]]});
globalThis.tetris.PIECES = PIECES;
var empty_board = function () {
return squint_core.vec(squint_core.repeat(globalThis.tetris.ROWS, squint_core.vec(squint_core.repeat(globalThis.tetris.COLS, null))));

};
globalThis.tetris.empty_board = empty_board;
var rand_piece = function () {
const k1 = squint_core.rand_nth(squint_core.keys(globalThis.tetris.PIECES));
return ({"kind": k1, "cells": squint_core.get(globalThis.tetris.PIECES, k1), "pos": [squint_core.quot(globalThis.tetris.COLS, 2), 0]});

};
globalThis.tetris.rand_piece = rand_piece;
if ((typeof tetris !== 'undefined') && (typeof tetris.state !== 'undefined')) {
} else {
var state = squint_core.atom(({"board": globalThis.tetris.empty_board(), "piece": globalThis.tetris.rand_piece(), "score": 0, "lines": 0, "over?": false, "paused?": false}));
globalThis.tetris.state = state;
};
var piece_cells = function (piece) {
const vec__14 = squint_core.get(piece, "pos");
const px5 = squint_core.nth(vec__14, 0, null);
const py6 = squint_core.nth(vec__14, 1, null);
return squint_core.map((function (p__28) {
const vec__710 = p__28;
const dx11 = squint_core.nth(vec__710, 0, null);
const dy12 = squint_core.nth(vec__710, 1, null);
return [(px5 + dx11), (py6 + dy12)];

}), squint_core.get(piece, "cells"));

};
globalThis.tetris.piece_cells = piece_cells;
var in_bounds_QMARK_ = function (p__29) {
const vec__14 = p__29;
const x5 = squint_core.nth(vec__14, 0, null);
const y6 = squint_core.nth(vec__14, 1, null);
return ((0 <= x5) && ((x5 < globalThis.tetris.COLS) && (y6 < globalThis.tetris.ROWS)));

};
globalThis.tetris.in_bounds_QMARK_ = in_bounds_QMARK_;
var collides_QMARK_ = function (board, piece) {
return squint_core.some((function (p__30) {
const vec__14 = p__30;
const x5 = squint_core.nth(vec__14, 0, null);
const y6 = squint_core.nth(vec__14, 1, null);
const or__29813__auto__7 = (x5 < 0);
if (or__29813__auto__7) {
return or__29813__auto__7} else {
const or__29813__auto__8 = (x5 >= globalThis.tetris.COLS);
if (or__29813__auto__8) {
return or__29813__auto__8} else {
const or__29813__auto__9 = (y6 >= globalThis.tetris.ROWS);
if (or__29813__auto__9) {
return or__29813__auto__9} else {
return ((y6 >= 0) && squint_core.get_in(board, [y6, x5]))};
};
};

}), globalThis.tetris.piece_cells(piece));

};
globalThis.tetris.collides_QMARK_ = collides_QMARK_;
var rotate_cells = function (cells) {
return squint_core.mapv((function (p__31) {
const vec__14 = p__31;
const x5 = squint_core.nth(vec__14, 0, null);
const y6 = squint_core.nth(vec__14, 1, null);
return [(-y6), x5];

}), cells);

};
globalThis.tetris.rotate_cells = rotate_cells;
var lock_piece = function (board, piece) {
return squint_core.reduce((function (b, p__32) {
const vec__14 = p__32;
const x5 = squint_core.nth(vec__14, 0, null);
const y6 = squint_core.nth(vec__14, 1, null);
if (squint_core.truth_(((y6 >= 0) && globalThis.tetris.in_bounds_QMARK_([x5, y6])))) {
return squint_core.assoc_in(b, [y6, x5], squint_core.get(piece, "kind"))} else {
return b};

}), board, globalThis.tetris.piece_cells(piece));

};
globalThis.tetris.lock_piece = lock_piece;
var clear_lines = function (board) {
const kept1 = squint_core.vec(squint_core.remove((function (row) {
return squint_core.every_QMARK_(squint_core.some_QMARK_, row);

}), board));
const cleared2 = (globalThis.tetris.ROWS - squint_core.count(kept1));
const pad3 = squint_core.vec(squint_core.repeat(cleared2, squint_core.vec(squint_core.repeat(globalThis.tetris.COLS, null))));
return [squint_core.into(pad3, kept1), cleared2];

};
globalThis.tetris.clear_lines = clear_lines;
var SCORES = (() => {
const G__331 = ({});
(G__331[0] = 0);
(G__331[1] = 100);
(G__331[2] = 300);
(G__331[3] = 500);
(G__331[4] = 800);
return G__331;

})();
globalThis.tetris.SCORES = SCORES;
var try_move = function (s, dx, dy) {
const p_SINGLEQUOTE_1 = squint_core.update(squint_core.get(s, "piece"), "pos", (function (p__34) {
const vec__25 = p__34;
const x6 = squint_core.nth(vec__25, 0, null);
const y7 = squint_core.nth(vec__25, 1, null);
return [(x6 + dx), (y7 + dy)];

}));
if (squint_core.truth_(globalThis.tetris.collides_QMARK_(squint_core.get(s, "board"), p_SINGLEQUOTE_1))) {
return s} else {
return squint_core.assoc(s, "piece", p_SINGLEQUOTE_1)};

};
globalThis.tetris.try_move = try_move;
var try_rotate = function (s) {
const p1 = squint_core.get(s, "piece");
const p_SINGLEQUOTE_2 = ((("O" === squint_core.get(p1, "kind"))) ? (p1) : (squint_core.update(p1, "cells", globalThis.tetris.rotate_cells)));
if (squint_core.truth_(globalThis.tetris.collides_QMARK_(squint_core.get(s, "board"), p_SINGLEQUOTE_2))) {
return s} else {
return squint_core.assoc(s, "piece", p_SINGLEQUOTE_2)};

};
globalThis.tetris.try_rotate = try_rotate;
var step_down = function (s) {
const p1 = squint_core.get(s, "piece");
const p_SINGLEQUOTE_2 = squint_core.update(p1, "pos", (function (p__35) {
const vec__36 = p__35;
const x7 = squint_core.nth(vec__36, 0, null);
const y8 = squint_core.nth(vec__36, 1, null);
return [x7, (y8 + 1)];

}));
if (squint_core.truth_(globalThis.tetris.collides_QMARK_(squint_core.get(s, "board"), p_SINGLEQUOTE_2))) {
const locked12 = globalThis.tetris.lock_piece(squint_core.get(s, "board"), p1);
const vec__913 = globalThis.tetris.clear_lines(locked12);
const board_SINGLEQUOTE_14 = squint_core.nth(vec__913, 0, null);
const n15 = squint_core.nth(vec__913, 1, null);
const next_piece16 = globalThis.tetris.rand_piece();
const over_QMARK_17 = squint_core.boolean$(globalThis.tetris.collides_QMARK_(board_SINGLEQUOTE_14, next_piece16));
return squint_core.update(squint_core.update(squint_core.assoc(s, "board", board_SINGLEQUOTE_14, "piece", next_piece16, "over?", over_QMARK_17), "score", squint_core._PLUS_, squint_core.get(globalThis.tetris.SCORES, n15, 0)), "lines", squint_core._PLUS_, n15);
} else {
return squint_core.assoc(s, "piece", p_SINGLEQUOTE_2)};

};
globalThis.tetris.step_down = step_down;
var hard_drop = function (s) {
let s1 = s;
while(true){
const p2 = squint_core.get(s1, "piece");
const p_SINGLEQUOTE_3 = squint_core.update(p2, "pos", (function (p__36) {
const vec__47 = p__36;
const x8 = squint_core.nth(vec__47, 0, null);
const y9 = squint_core.nth(vec__47, 1, null);
return [x8, (y9 + 1)];

}));
if (squint_core.truth_(globalThis.tetris.collides_QMARK_(squint_core.get(s1, "board"), p_SINGLEQUOTE_3))) {
return globalThis.tetris.step_down(s1)} else {
let G__10 = squint_core.assoc(s1, "piece", p_SINGLEQUOTE_3);
s1 = G__10;
continue;
};
;break;
}
;

};
globalThis.tetris.hard_drop = hard_drop;
var CELL = 24;
globalThis.tetris.CELL = CELL;
var render_board = function (s) {
const b1 = squint_core.get(s, "board");
const piece2 = squint_core.get(s, "piece");
const active3 = squint_core.into(({}), squint_core.map((function (c) {
return [c, squint_core.get(piece2, "kind")];

}), globalThis.tetris.piece_cells(piece2)));
const cell_color4 = (function (x, y) {
const or__29813__auto__5 = squint_core.get(active3, [x, y]);
if (squint_core.truth_(or__29813__auto__5)) {
return or__29813__auto__5} else {
return squint_core.get_in(b1, [y, x])};

});
return ["div", ({"style": ({"display": "inline-grid", "grid-template-columns": `${"repeat("}${globalThis.tetris.COLS??''}${", "}${globalThis.tetris.CELL??''}${"px)"}`, "grid-template-rows": `${"repeat("}${globalThis.tetris.ROWS??''}${", "}${globalThis.tetris.CELL??''}${"px)"}`, "gap": "1px", "background": "#111", "padding": "4px", "border-radius": "6px"})}), squint_core.lazy((function* () {
for (let G__6 of squint_core.iterable(squint_core.range(globalThis.tetris.ROWS))) {
const y7 = G__6;
for (let G__8 of squint_core.iterable(squint_core.range(globalThis.tetris.COLS))) {
const x9 = G__8;
const k10 = cell_color4(x9, y7);
yield ["div", ({"key": `${x9??''}-${y7??''}`, "style": ({"width": `${globalThis.tetris.CELL??''}px`, "height": `${globalThis.tetris.CELL??''}px`, "background": (yield* (function* () {
const or__29813__auto__11 = squint_core.get(globalThis.tetris.COLORS, k10);
if (squint_core.truth_(or__29813__auto__11)) {
return or__29813__auto__11} else {
return "#1f2937"};

})()), "border-radius": "3px"})})];
}
}
return null;

}))];

};
globalThis.tetris.render_board = render_board;
var panel = function (s) {
return ["div", ({"style": ({"font": "14px system-ui", "color": "#e5e7eb", "margin-left": "16px", "min-width": "160px"})}), ["div", ({"style": ({"font": "600 11px system-ui", "letter-spacing": ".08em", "text-transform": "uppercase", "color": "#fb7185", "margin-bottom": "8px"})}), "tetris - reagami"], ["div", "Score: ", squint_core.get(s, "score")], ["div", "Lines: ", squint_core.get(s, "lines")], ((squint_core.truth_(squint_core.get(s, "over?"))) ? (["div", ({"style": ({"margin-top": "10px", "color": "#fca5a5"})}), "GAME OVER"]) : (null)), ((squint_core.truth_(squint_core.get(s, "paused?"))) ? (["div", ({"style": ({"margin-top": "10px", "color": "#fcd34d"})}), "PAUSED"]) : (null)), ["div", ({"style": ({"margin-top": "12px", "font-size": "12px", "color": "#9ca3af", "line-height": "1.6"})}), "<- -> move", ["br"], "up rotate", ["br"], "down soft drop", ["br"], "space hard drop", ["br"], "p pause, r reset"], ["a", ({"href": "https://github.com/squint-cljs/squint/tree/main/examples/tetris", "target": "_blank", "rel": "noopener", "style": ({"display": "inline-block", "margin-top": "14px", "font-size": "12px", "color": "#93c5fd", "text-decoration": "none", "border-bottom": "1px dotted #93c5fd"})}), "view source on github"]];

};
globalThis.tetris.panel = panel;
var root = function () {
const s1 = squint_core.deref(globalThis.tetris.state);
return ["div", ({"style": ({"display": "inline-flex", "align-items": "flex-start", "font-family": "system-ui, sans-serif", "background": "#0b1020", "padding": "16px", "border-radius": "10px", "margin": "10px"})}), [globalThis.tetris.render_board, s1], [globalThis.tetris.panel, s1]];

};
globalThis.tetris.root = root;
var host_el = function () {
const el1 = (() => {
const or__29813__auto__2 = document.querySelector("#tetris");
if (squint_core.truth_(or__29813__auto__2)) {
return or__29813__auto__2} else {
const el3 = document.createElement("div");
el3.id = "tetris";
document.body.appendChild(el3);
return el3;
};

})();
document.body.style.background = "linear-gradient(135deg,#0b1020,#1e1b4b)";
document.body.style.minHeight = "100vh";
document.body.style.margin = "0";
el1.style.display = "flex";
el1.style.justifyContent = "center";
el1.style.paddingTop = "32px";
for (let G__4 of squint_core.iterable(Array.from(document.body.children))) {
const sib5 = G__4;
if (!squint_core._EQ_(sib5, el1)) {
sib5.style.display = "none"}
};
return el1;

};
globalThis.tetris.host_el = host_el;
var render_BANG_ = function () {
return globalThis.tetris.reagami.render(globalThis.tetris.host_el(), [globalThis.tetris.root]);

};
globalThis.tetris.render_BANG_ = render_BANG_;
if ((typeof tetris !== 'undefined') && (typeof tetris._watch !== 'undefined')) {
} else {
var _watch = squint_core.add_watch(globalThis.tetris.state, "tetris/render", (function (_, _1, _2, _3) {
return globalThis.tetris.render_BANG_();

}));
globalThis.tetris._watch = _watch;
};
var reset_game_BANG_ = function () {
return squint_core.reset_BANG_(globalThis.tetris.state, ({"board": globalThis.tetris.empty_board(), "piece": globalThis.tetris.rand_piece(), "score": 0, "lines": 0, "over?": false, "paused?": false}));

};
globalThis.tetris.reset_game_BANG_ = reset_game_BANG_;
var on_key = function (e) {
const k1 = e.key;
const s2 = squint_core.deref(globalThis.tetris.state);
if (squint_core.truth_(squint_core.get(s2, "over?"))) {
} else {
const G__373 = k1;
switch (G__373) {case "ArrowLeft":
e.preventDefault();
squint_core.swap_BANG_(globalThis.tetris.state, globalThis.tetris.try_move, -1, 0);

break;
case "ArrowRight":
e.preventDefault();
squint_core.swap_BANG_(globalThis.tetris.state, globalThis.tetris.try_move, 1, 0);

break;
case "ArrowDown":
e.preventDefault();
squint_core.swap_BANG_(globalThis.tetris.state, globalThis.tetris.try_move, 0, 1);

break;
case "ArrowUp":
e.preventDefault();
squint_core.swap_BANG_(globalThis.tetris.state, globalThis.tetris.try_rotate);

break;
case " ":
e.preventDefault();
squint_core.swap_BANG_(globalThis.tetris.state, globalThis.tetris.hard_drop);

break;
case "p":
squint_core.swap_BANG_(globalThis.tetris.state, squint_core.update, "paused?", squint_core.not);

break;
case "r":
globalThis.tetris.reset_game_BANG_();

break;
}};
if (squint_core.truth_((() => {
const and__29847__auto__5 = squint_core.get(s2, "over?");
if (squint_core.truth_(and__29847__auto__5)) {
return ("r" === k1)} else {
return and__29847__auto__5};

})())) {
return globalThis.tetris.reset_game_BANG_();
};

};
globalThis.tetris.on_key = on_key;
if ((typeof tetris !== 'undefined') && (typeof tetris._key !== 'undefined')) {
} else {
var _key = (() => {
window.addEventListener("keydown", globalThis.tetris.on_key);
return true
})();
globalThis.tetris._key = _key;
};
if ((typeof tetris !== 'undefined') && (typeof tetris._timer !== 'undefined')) {
} else {
var _timer = setInterval((function () {
const s1 = squint_core.deref(globalThis.tetris.state);
if (squint_core.truth_((() => {
const or__29813__auto__2 = squint_core.get(s1, "over?");
if (squint_core.truth_(or__29813__auto__2)) {
return or__29813__auto__2} else {
return squint_core.get(s1, "paused?")};

})())) {
return null} else {
return squint_core.swap_BANG_(globalThis.tetris.state, globalThis.tetris.step_down);
};

}), globalThis.tetris.TICK_MS);
globalThis.tetris._timer = _timer;
};
globalThis.tetris.render_BANG_();

export { try_move, on_key, _key, collides_QMARK_, _watch, CELL, SCORES, render_board, empty_board, root, PIECES, COLS, TICK_MS, hard_drop, lock_piece, reset_game_BANG_, render_BANG_, state, piece_cells, step_down, try_rotate, panel, ROWS, rand_piece, COLORS, host_el, rotate_cells, in_bounds_QMARK_, clear_lines, _timer }
