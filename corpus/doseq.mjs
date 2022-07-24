import { seq, first, unchecked_inc, chunk_first, truth_, prn, chunk_rest, chunked_seq_QMARK_, vector, next, count } from 'cherry-cljs/cljs.core.js'
let seq__15 = seq(vector(1, 2, 3));
let chunk__26 = null;
let count__37 = 0;
let i__48 = 0;
while(true){
if (truth_((i__48 < count__37))) {
let x9 = _nth(chunk__26, i__48);
prn(x9);
null;
let G__10 = seq__15;
let G__11 = chunk__26;
let G__12 = count__37;
let G__13 = unchecked_inc(i__48);
seq__15 = G__10;
chunk__26 = G__11;
count__37 = G__12;
i__48 = G__13;
continue;
} else {
let temp__28642__auto__14 = seq(seq__15);
if (truth_(temp__28642__auto__14)) {
let seq__115 = temp__28642__auto__14;
if (truth_(chunked_seq_QMARK_(seq__115))) {
let c__28792__auto__16 = chunk_first(seq__115);
let G__17 = chunk_rest(seq__115);
let G__18 = c__28792__auto__16;
let G__19 = count(c__28792__auto__16);
let G__20 = 0;
seq__15 = G__17;
chunk__26 = G__18;
count__37 = G__19;
i__48 = G__20;
continue;
} else {
let x21 = first(seq__115);
prn(x21);
null;
let G__22 = next(seq__115);
let G__23 = null;
let G__24 = 0;
let G__25 = 0;
seq__15 = G__22;
chunk__26 = G__23;
count__37 = G__24;
i__48 = G__25;
continue;
}}};break;
}
;

export {  }
