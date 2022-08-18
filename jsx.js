import { readFileSync } from 'fs';
const code = readFileSync('test.jsx');
const transpiler = new Bun.Transpiler({ loader: "jsx" });

transpiler.transformSync(code);
