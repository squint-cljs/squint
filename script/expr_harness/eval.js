// Evaluates compiled forms directly and through Datastar's value attribute splitter.
// Run with: bb expr-harness
import fs from 'node:fs';
import * as squint_core from '../../src/squint/core.js';

globalThis.squint_core = squint_core;

const [, , compiledPath] = process.argv;

// Datastar v1.0.2, library/src/engine/engine.ts, genRx
const statementRe = /(\/(\\\/|[^/])*\/|"(\\"|[^"])*"|'(\\'|[^'])*'|`(\\`|[^`])*`|\(\s*((function)\s*\(\s*\)|(\(\s*\))\s*=>)\s*(?:\{[\s\S]*?\}|[^;){]*)\s*\)\s*\(\s*\)|[^;])+/gm;
const signalRe = /("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\$]|\$(?!\{))*`)|\$\{([^{}]*)\}|\$([a-zA-Z_\d]\w*(?:[.-]\w+)*)/g;
const innerSignalRe = /\$([a-zA-Z_\d]\w*(?:[.-]\w+)*)/g;

const signalPath = (name) => name.split('.').reduce((acc, part) => `${acc}['${part}']`, '$');

const rewriteSignals = (js) =>
  js.trim().replace(signalRe, (match, quoted, interpolation, signal) => {
    if (quoted) return match;
    if (interpolation !== undefined) {
      return '${' + interpolation.replace(innerSignalRe, (_, name) => signalPath(name)) + '}';
    }
    return signalPath(signal);
  });

const valueAttribute = (js) => {
  const statements = rewriteSignals(js).match(statementRe);
  const last = statements.length - 1;
  if (!statements[last].trim().startsWith('return')) {
    statements[last] = `return (${statements[last].trim()});`;
  }
  return statements.join(';\n');
};

const signals = () => ({ a: null, b: 7, c: 0, d: '', e: false, xs: [1, 2, 3], z: undefined });

const run = (body) => {
  try {
    return Function('el', '$', 'evt', body)({}, signals(), {});
  } catch (e) {
    return `${e.name}: ${e.message}`;
  }
};

const same = (x, y) => JSON.stringify(x ?? null) === JSON.stringify(y ?? null);

const rows = JSON.parse(fs.readFileSync(compiledPath, 'utf8'));
let direct = 0;
let datastar = 0;
let bytes = 0;
let iifes = 0;

for (const { src, expected, js } of rows) {
  const directResult = run(`return (${rewriteSignals(js)});`);
  const datastarResult = run(valueAttribute(js));
  const directOk = same(directResult, expected);
  const datastarOk = same(datastarResult, expected);
  direct += directOk;
  datastar += datastarOk;
  bytes += js.length;
  iifes += (js.match(/\(\(\) =>|\(function\*? \(\)/g) || []).length;
  const detail = directOk && datastarOk
    ? ''
    : `   direct=${JSON.stringify(directResult)} datastar=${JSON.stringify(datastarResult)} expected=${JSON.stringify(expected)}`;
  console.log(`${directOk ? 'ok ' : 'BAD'} ${datastarOk ? 'ok ' : 'BAD'} ${String(js.length).padStart(4)}B  ${src}${detail}`);
}

console.log(`\nTOTAL forms=${rows.length} direct-ok=${direct} datastar-ok=${datastar} bytes=${bytes} no-arg-iifes=${iifes}`);

if (direct < rows.length || datastar < rows.length) process.exit(1);
