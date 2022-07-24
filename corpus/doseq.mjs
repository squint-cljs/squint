import { seq, first, unchecked_inc, chunk_first, truth_, prn, chunk_rest, chunked_seq_QMARK_, vector, keyword, next, count } from 'cherry-cljs/cljs.core.js'
let seq__113 = seq(vector(1, 2, 3));
let chunk__614 = null;
let count__715 = 0;
let i__816 = 0;
while(true){
if (truth_((i__816 < count__715))) {
let x17 = _nth(chunk__614, i__816);
let seq__918 = seq(vector(keyword("hello"), keyword("bye")));
let chunk__1019 = null;
let count__1120 = 0;
let i__1221 = 0;
while(true){
if (truth_((i__1221 < count__1120))) {
let y22 = _nth(chunk__1019, i__1221);
prn(x17, y22);
null;
let G__23 = seq__918;
let G__24 = chunk__1019;
let G__25 = count__1120;
let G__26 = unchecked_inc(i__1221);
seq__918 = G__23;
chunk__1019 = G__24;
count__1120 = G__25;
i__1221 = G__26;
continue;
} else {
let temp__28532__auto__27 = seq(seq__918);
if (truth_(temp__28532__auto__27)) {
let seq__928 = temp__28532__auto__27;
if (truth_(chunked_seq_QMARK_(seq__928))) {
let c__28634__auto__29 = chunk_first(seq__928);
let G__30 = chunk_rest(seq__928);
let G__31 = c__28634__auto__29;
let G__32 = count(c__28634__auto__29);
let G__33 = 0;
seq__918 = G__30;
chunk__1019 = G__31;
count__1120 = G__32;
i__1221 = G__33;
continue;
} else {
let y34 = first(seq__928);
prn(x17, y34);
null;
let G__35 = next(seq__928);
let G__36 = null;
let G__37 = 0;
let G__38 = 0;
seq__918 = G__35;
chunk__1019 = G__36;
count__1120 = G__37;
i__1221 = G__38;
continue;
}}};break;
}
;
let G__39 = seq__113;
let G__40 = chunk__614;
let G__41 = count__715;
let G__42 = unchecked_inc(i__816);
seq__113 = G__39;
chunk__614 = G__40;
count__715 = G__41;
i__816 = G__42;
continue;
} else {
let temp__28532__auto__43 = seq(seq__113);
if (truth_(temp__28532__auto__43)) {
let seq__144 = temp__28532__auto__43;
if (truth_(chunked_seq_QMARK_(seq__144))) {
let c__28634__auto__45 = chunk_first(seq__144);
let G__46 = chunk_rest(seq__144);
let G__47 = c__28634__auto__45;
let G__48 = count(c__28634__auto__45);
let G__49 = 0;
seq__113 = G__46;
chunk__614 = G__47;
count__715 = G__48;
i__816 = G__49;
continue;
} else {
let x50 = first(seq__144);
let seq__251 = seq(vector(keyword("hello"), keyword("bye")));
let chunk__352 = null;
let count__453 = 0;
let i__554 = 0;
while(true){
if (truth_((i__554 < count__453))) {
let y55 = _nth(chunk__352, i__554);
prn(x50, y55);
null;
let G__56 = seq__251;
let G__57 = chunk__352;
let G__58 = count__453;
let G__59 = unchecked_inc(i__554);
seq__251 = G__56;
chunk__352 = G__57;
count__453 = G__58;
i__554 = G__59;
continue;
} else {
let temp__28532__auto__60 = seq(seq__251);
if (truth_(temp__28532__auto__60)) {
let seq__261 = temp__28532__auto__60;
if (truth_(chunked_seq_QMARK_(seq__261))) {
let c__28634__auto__62 = chunk_first(seq__261);
let G__63 = chunk_rest(seq__261);
let G__64 = c__28634__auto__62;
let G__65 = count(c__28634__auto__62);
let G__66 = 0;
seq__251 = G__63;
chunk__352 = G__64;
count__453 = G__65;
i__554 = G__66;
continue;
} else {
let y67 = first(seq__261);
prn(x50, y67);
null;
let G__68 = next(seq__261);
let G__69 = null;
let G__70 = 0;
let G__71 = 0;
seq__251 = G__68;
chunk__352 = G__69;
count__453 = G__70;
i__554 = G__71;
continue;
}}};break;
}
;
let G__72 = next(seq__144);
let G__73 = null;
let G__74 = 0;
let G__75 = 0;
seq__113 = G__72;
chunk__614 = G__73;
count__715 = G__74;
i__816 = G__75;
continue;
}}};break;
}
;

export {  }
