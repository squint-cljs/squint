import { prn } from 'cherry-cljs/cljs.core.js'
import * as fs from 'fs';
import { version } from 'process';
console.log(version);
prn(fs.readFileSync("corpus/ns_form.cljs", "utf-8"));
