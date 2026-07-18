// ADR 0007 - ceiling measurement. Rewrites every protocol-slot access in a
// core.js to (void 0) so esbuild folds the dispatch branches away AND drops the
// now-unused slot symbols. Produces the "protocol-free" ceiling core (all slot
// dispatch gone). Bundle the reagami app against it to see the max recoverable.
//   node measure-ceiling.cjs <core.js in> <core-free.js out>
const { readFileSync, writeFileSync } = require('node:fs');
const [inp, out] = process.argv.slice(2);
let src = readFileSync(inp, 'utf8');
// drop assignment statements targeting a slot:  X[Islot] = Y;
src = src.split('\n').filter(l => !/\w+\[I[A-Z][A-Za-z]*__[A-Za-z_?!]+\]\s*=[^=]/.test(l)).join('\n');
// slot member reads  obj[Islot] / obj?.[Islot]  ->  (void 0)
// makes `if ((void 0) !== undefined)` fold to false, so esbuild drops the branch
src = src.replace(/[A-Za-z_$][\w$]*(?:\?\.)?\[(I[A-Z][A-Za-z]*__[A-Za-z_?!]+)\]/g, '(void 0)');
writeFileSync(out, src);
console.log('wrote', out);
