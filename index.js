mport { transpileFile as t} from  './lib/transpiler.js'
import { js__GT_clj, keyword, clj__GT_js, str } from './cljs.core.js'

function transpileFile({ inFile }) {
  const resClj = t(js__GT_clj({"in-file": inFile}, keyword("keywordize-keys"), true));
  const resJ = clj__GT_js(resClj);
  return { outFile: resJ["out-file"]};
}

export { transpileFile }
