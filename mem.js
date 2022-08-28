// run with: node --expose-gc mem.js

import { first, rest, list, es6_iterator, map, range, take, vec } from './core.js';

class Cons {
  constructor(head, tail) {
    this.head = head;
    this.tail = tail;
  }
  *[Symbol.iterator]() {
    yield this.head;
    if (this.tail)
      yield* this.tail;
  }
}

function cons(head, tail) {
  return new Cons(head, tail);
}

function rest2(theCons) {
  return theCons.tail;
}

global.gc();

console.log(process.memoryUsage());

function doit() {
  var y = [new Array(10000000).fill(0), (new Array(10000000).fill(0)), 2];

  global.gc();

  console.log(process.memoryUsage());

  var rzz = rest(rest(y));

  var rzz2 = rest(rest(y));

  return first(rzz);
}

console.log(doit());

global.gc();

console.log(process.memoryUsage());

function doit2() {
  var rng = range();
  var seq1 = map((x) => { console.log(x); return x;}, rng);
  var seq2 = take(2, seq1);
  return vec(seq2);
}

console.log(doit2());

global.gc();

console.log(process.memoryUsage());
