import { reduce, comp, conj, inc, range, even_QMARK_ } from 'clavascript/core.js';

export const ITransformer = Symbol('ITransformer');
export const ITransformer_init = Symbol('ITransformer_init');
export const ITransformer_step = Symbol('ITransformer_init');
export const ITransformer_result = Symbol('ITransformer_init');

// external libs like cognitect/transducers-js, ramda, etc. use strings
let str_protocol = {
  [ITransformer_init]: '@@transducer/init',
  [ITransformer_step]: '@@transducer/step',
  [ITransformer_result]: '@@transducer/result',
};

// .call ensures that we maintain `this` even though we tear the method off
export function init(tf) {
  return (tf[str_protocol[ITransformer_init]] || tf[ITransformer_init]).call(tf, tf);
}

export function step(tf, res, el) {
  return (tf[str_protocol[ITransformer_step]] || tf[ITransformer_step]).call(tf, tf, res, el);
}

export function result(tf, res) {
  return (tf[str_protocol[ITransformer_result]] || tf[ITransformer_result]).call(tf, tf, res);
}

class BaseTransformer {
  [ITransformer_init](_) {
    return init(this.tf);
  }
  [ITransformer_result](_, res) {
    return result(this.tf, res);
  }
  [ITransformer_step](_, res, el) {
    return step(this.tf, res, el);
  }
}

// extend reducer protocol to functions, allowing Clojure-style arity style
Function.prototype[ITransformer_init] = function init(f) {
  return f();
};
Function.prototype[ITransformer_result] = function result(f, res) {
  return f(res);
};
Function.prototype[ITransformer_step] = function step(f, res, el) {
  return f(res, el);
};

class Mapping extends BaseTransformer {
  constructor(f, tf) {
    super();
    this.f = f;
    this.tf = tf;
  }
  [ITransformer_step](_, res, el) {
    return step(this.tf, res, this.f(el));
  }
}

export function map(f) {
  return (tf) => new Mapping(f, tf);
}

class Filter extends BaseTransformer {
  constructor(pred, tf) {
    super();
    this.pred = pred;
    this.tf = tf;
  }
  [ITransformer_step](_, res, el) {
    if (this.pred(el)) return step(this.tf, res, el);
    return res;
  }
}

export function filter(pred) {
  return (tf) => new Filter(pred, tf);
}

export function transduce(rf, tf, init, coll) {
  let tf2 = tf(rf);
  return reduce((res, el) => step(tf2, res, el), init, coll);
}

export function into(to, tf, from) {
  return transduce(conj, tf, to, from);
}

/*
 * testing stuff
 */

init(
  new Filter(even_QMARK_, {
    [ITransformer_init](_) {
      return 1;
    },
  })
);

console.log(into([], map(inc), range(10)));

console.log(into([], filter(even_QMARK_), range(10)));

console.log(into([], comp(filter(even_QMARK_), map(inc)), range(10)));

console.log(
  comp(
    (x) => x + '3',
    (x) => x + '2',
    (x) => x + '1'
  )('0')
);
