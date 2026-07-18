// ADR 0007 - registry measurement. Collapses all protocol slot symbols to ONE,
// keeping every dispatch branch intact - i.e. exactly what a registry recovers
// (the symbols; the branch code stays inline). Bundle the reagami app against
// the output to see the registry's real payoff.
//   node measure-registry.cjs <core.js in> <core-symonly.js out>
const { readFileSync, writeFileSync } = require('node:fs');
const [inp, out] = process.argv.slice(2);
let src = readFileSync(inp, 'utf8');
src = src.split('\n').map(l =>
  /^export const I[A-Z][A-Za-z]*__[A-Za-z_?!]+ = .*Symbol\(/.test(l) ? '' : l
).join('\n');
src = src.replace(/\bI[A-Z][A-Za-z]*__[A-Za-z_?!]+\b/g, '$S');
src = src.replace(/^(import [^\n]*\n)/, `$1const $S = /* @__PURE__ */ Symbol('s');\n`);
writeFileSync(out, src);
console.log('wrote', out);
