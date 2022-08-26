import { reduce, ensure_reduced, comp, conj, inc, range, even_QMARK_ } from 'clavascript/core.js';

/*
 * Transformer protocol
 * see https://github.com/cognitect-labs/transducers-js/issues/20
 * for historical inspiration & context
 */

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
// TODO fix arity for string protocol
export function init(xf) {
  return (xf[str_protocol[ITransformer_init]] || xf[ITransformer_init]).call(xf, xf);
}

export function step(xf, res, el) {
  return (xf[str_protocol[ITransformer_step]] || xf[ITransformer_step]).call(xf, xf, res, el);
}

export function result(xf, res) {
  return (xf[str_protocol[ITransformer_result]] || xf[ITransformer_result]).call(xf, xf, res);
}

// BaseTransformer provides default impls, as not every transducer op needs
// to implement a special init/result
class BaseTransformer {
  [ITransformer_init](_) {
    return init(this.xf);
  }
  [ITransformer_result](_, res) {
    return result(this.xf, res);
  }
  [ITransformer_step](_, res, el) {
    return step(this.xf, res, el);
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

/*
 * map
 */

class Mapping extends BaseTransformer {
  constructor(f, xf) {
    super();
    this.f = f;
    this.xf = xf;
  }
  [ITransformer_step](_, res, el) {
    return step(this.xf, res, this.f(el));
  }
}

export function map(f) {
  return (xf) => new Mapping(f, xf);
}

/*
 * filter
 */

class Filter extends BaseTransformer {
  constructor(pred, xf) {
    super();
    this.pred = pred;
    this.xf = xf;
  }
  [ITransformer_step](_, res, el) {
    if (this.pred(el)) return step(this.xf, res, el);
    return res;
  }
}

export function filter(pred) {
  return (xf) => new Filter(pred, xf);
}

/*
 * take
 */

class Take extends BaseTransformer {
  constructor(n, xf) {
    super();
    this.n = n;
    this.xf = xf;
  }
  [ITransformer_step](_, res, el) {
    let ret;
    if (this.n > 0) {
      this.n -= 1;
      ret = step(this.xf, res, el);
    }
    if (this.n > 0) {
      return ret;
    }
    return ensure_reduced(ret);
  }
}

export function take(n) {
  return (xf) => new Take(n, xf);
}

/*
 * Using transducers
 */

export function transduce(rf, xf, init, coll) {
  let xf2 = xf(rf);
  return reduce((res, el) => step(xf2, res, el), init, coll);
}

export function into(to, xf, from) {
  return transduce(conj, xf, to, from);
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

console.log(into([], comp(filter(even_QMARK_), map(inc), take(3)), range(10)));
