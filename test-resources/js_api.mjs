import { compileStringEx } from 'squint-cljs/index.js';

// repl = output suitable for REPL
// async = start in async mode (allows top level awaits)
// context: 'return' = start in return context since we're going to wrap the result in a self-calling function
// elide-exports: do not emit exports, since they cannot be evaluated by `eval`
const opts = {repl: true, async: true, context: 'return', "elide-exports": true};
let state = null;
state = compileStringEx('(ns foo) (def x 1) x', opts, state);

// we evaluate REPL output in an async function since it may contain top level awaits
function wrappedInAsyncFn(s) {
  return `(async function() {\n${s}\n})()`;
}

// the following gives 1 like expected
console.log(await eval(wrappedInAsyncFn(state.javascript)));

// pass res1 which contains compiler state
state = compileStringEx('x', opts, state);
// since we're evaluating x in the same namespace it was defined in, evaluating
// the returned javascript gives 1
console.log(await eval(wrappedInAsyncFn(state.javascript)));
