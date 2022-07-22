import { transpileFile } from './index.js';
import { spawnSync } from 'child_process';

const inFile = process.argv[2];

const { outFile } = transpileFile({ inFile });

await import(`./${outFile}`);
