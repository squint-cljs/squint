import { default_extensions, complete_keymap } from '@nextjournal/clojure-mode';
import { history, historyKeymap } from '@codemirror/commands';
import * as eval_region from '@nextjournal/clojure-mode/extensions/eval-region';
import { EditorView, drawSelection, keymap } from '@codemirror/view';
import { EditorState } from '@codemirror/state';
import { syntaxHighlighting, defaultHighlightStyle, foldGutter } from '@codemirror/language';
import { javascript } from "@codemirror/lang-javascript";
import prettierPluginJS from "prettier/plugins/estree.mjs";
import prettierPluginBabel from "prettier/plugins/babel.mjs";
import React from "react";
import ReactDOM from "react-dom/client";
import { Inspector } from "react-inspector";
const url = new URL(window.location.href);
import * as squint from "squint-cljs";
import * as prettier from "prettier";

const theme = EditorView.theme({
  '.cm-content': {
    whitespace: 'pre-wrap',
    passing: '10px 0',
    flex: '1 1 0',
  },

  '&.cm-focused': { outline: '0 !important' },
  '.cm-line': {
    padding: '0 9px',
    'line-height': '1.6',
    'font-size': '16px',
    'font-family': 'var(--code-font)',
  },
  '.cm-matchingBracket': {
    'border-bottom': '1px solid var(--teal-color)',
    color: 'inherit',
  },
  '.cm-gutters': {
    background: 'transparent',
    border: 'none',
  },
  '.cm-gutterElement': { 'margin-left': '5px' },
  // only show cursor when focused
  '.cm-cursor': { visibility: 'hidden' },
  '&.cm-focused .cm-cursor': { visibility: 'visible' },
});

const squintExtension = (opts) => {
  return keymap.of([
    { key: 'Alt-Enter', run: evalCell },
    {
      key: opts.modifier + '-Enter',
      run: evalAtCursor,
      shift: evalToplevel,
    },
  ]);
};

const urlParams = new URLSearchParams(window.location.search);
var repl = urlParams.get('repl') !== 'false';

class LazyIterable extends Array {
  constructor(arr) {
    super(arr);
  }
}

let compilerState = null;

let jsEditor = null;

async function JSEditor(js) {
  js = await prettier.format(js, { parser: "babel", plugins: [prettierPluginJS, prettierPluginBabel] });
  js = js.trim();
  const elt = document.querySelector('#compiledCode');
  const fontSizeTheme = EditorView.theme({
    $: {
      fontSize: "5pt"
    }
  });
  let extensions = [javascript({}).language, fontSizeTheme, foldGutter(), syntaxHighlighting(defaultHighlightStyle),
  EditorView.editable.of(false)];
  let state = EditorState.create({
    doc: js,
    extensions: extensions,
  });
  if (jsEditor) {
    jsEditor.destroy();
  }
  let editor = new EditorView({
    state: state,
    parent: elt,
    extensions: extensions,
  });
  jsEditor = editor;
}

let reactRoot = ReactDOM.createRoot(document.querySelector("#result"));

let evalCode = async (code) => {
  try {
    let importSource = url.searchParams.get('jsx.import-source') || 'react';
    let opts = { repl: repl, 'elide-exports': repl, context: repl ? 'return' : 'statement', "jsx-runtime": { "import-source": importSource, development: true },
                 "source-maps": true};
    compilerState = compileStringEx(`(do ${code}\n)`, opts, compilerState);
    console.log(compilerState);
    let jsBody = compilerState.imports + compilerState.body;
    let js = jsBody;
    if (repl) {
      js = `(async function() { ${js} })()`;
    }
    const sms = compilerState["source-maps"];
    const smsBase64 = btoa(sms);
    js += "\n//# sourceMappingURL=data:application/json;charset=utf-8;base64," + smsBase64;
    console.log(js);
    if (dev) {
      console.log("Loading local squint libs");
      js = js.replaceAll("'squint-cljs/", "'./squint-local/");
    }
    JSEditor(jsBody);
    if (!repl) {
      const encodedJs = encodeURIComponent(js);
      const dataUri =
        'data:text/javascript;charset=utf-8;eval=' + Date.now() + ',' + encodedJs;
      let result = await import(/* @vite-ignore */dataUri);
    } else {
      let result = await eval(js);
      if (result && result?.constructor?.name === 'LazyIterable') {
        let stdlib = (await import("squint-cljs/core.js"));
        let take_fn = stdlib.take;
        let concat_fn = stdlib.concat;
        let end = {};
        result = [...take_fn(10, concat_fn(result, [end]))];
        let isEnd = result[result.length - 1] !== end;
        if (isEnd) result.push('...')
        else result.pop();
        result = new LazyIterable(result);
      }
      reactRoot.render(React.createElement(Inspector, { data: result }));
      if (docChanged) {
        url.searchParams.delete('src');
        window.history.replaceState(null, null, url);
        localStorage.setItem('document', editor.state.doc.toString());
        docChanged = false;
      }
    }
  } catch (e) {
    document.querySelector('#result').innerText =
      e.message + '\n\n' + e.stack;
    console.error(e);
  }
};

let evalCell = (opts) => {
  let js = opts.state.doc.toString();
  evalCode(js);
  return true;
};

let evalAtCursor = (opts) => {
  if (!repl) return;
  let js = eval_region.cursor_node_string(opts.state).toString();
  evalCode(js);
  return true;
};

let docChanged = false;

let evalToplevel = (js) => {
  if (!repl) return;
  return true;
};
let updateExt = EditorView.updateListener.of((update) => {
  if (update.docChanged) docChanged = true;
});
const platform = navigator.platform;
const isMac = platform.startsWith('Mac') || platform.startsWith('iP');
let modifier = localStorage.getItem("editor.modifier") || (isMac ? 'Meta' : 'Ctrl');
if (!isMac || modifier === 'Ctrl') document.getElementById('modifierDesc').innerText = 'Ctrl'
let extensions = [
  history(),
  theme,
  foldGutter(),
  syntaxHighlighting(defaultHighlightStyle),
  drawSelection(),
  keymap.of(complete_keymap),
  keymap.of(historyKeymap),
  squintExtension({ modifier: modifier }),
  eval_region.extension({ modifier: modifier }),
  updateExt,
  ...default_extensions,
];

let doc = `
      (comment
  (fizz-buzz 1)
  (fizz-buzz 3)
  (fizz-buzz 5)
  (fizz-buzz 15)
  (fizz-buzz 17)
  (fizz-buzz 42))

(defn fizz-buzz [n]
  (condp (fn [a b] (zero? (mod b a))) n
    15 "fizzbuzz"
    3  "fizz"
    5  "buzz"
    n))

(require '["https://esm.sh/canvas-confetti@1.6.0$default" :as confetti])

(do
  (js-await (confetti))
  (+ 1 2 3))
`.trim();

const aocDoc = `
;; Helper functions:
;; (fetch-input year day) - get AOC input
;; (append str) - append str to DOM
;; (spy x) - log x to console and return x

;; Remember to update the year and day in the fetch-input call.
(def input (->> (js-await (fetch-input 2022 1))
             #_spy
             str/split-lines
             (mapv parse-long)))

(defn part-1
  []
  (->> input
    (partition-by nil?)
    (take-nth 2)
    (map #(apply + %))
    (apply max)))

(defn part-2
  []
  (->> input
    (partition-by nil?)
    (take-nth 2)
    (map #(apply + %))
    (sort-by -)
    (take 3)
    (apply +)))

(time (part-1))
#_(time (part-2))`.trim();
const aocBoilerplateUrl = 'https://gist.githubusercontent.com/borkdude/cf94b492d948f7f418aa81ba54f428ff/raw/a6e9992b079e20e21d753e8c75a7353c5908b225/aoc_ui.cljs';

const boilerplate = urlParams.get('boilerplate');
let boilerplateSrc;
if (boilerplate) {
  boilerplateSrc = await fetch(boilerplate).then((p) => p.text());
}
let src = urlParams.get('src');
if (src) {
  if (/http(s)?:\/\/.*/.test(src)) {
    src = await fetch(src).then((p) => p.text());
  } else {
    src = atob(src);
  }
  doc = src;
} else {
  doc = localStorage.getItem('document') || doc;
}
let state = EditorState.create({
  doc: doc,
  extensions: extensions,
});
let editorElt = document.querySelector('#editor');
let editor = new EditorView({
  state: state,
  parent: editorElt,
  extensions: extensions,
});
globalThis.editor = editor;
var dev = JSON.parse(urlParams.get('dev')) ?? location.hostname === 'localhost';


var squintCompiler = squint;
if (dev) {
  console.log('Loading development squint compiler');
  squintCompiler = await import('./squint-local/index.js');
}

var compileStringEx = squintCompiler.compileStringEx;
window.compile = () => {
  let code = editor.state.doc.toString();
  code = (boilerplateSrc || '') + '\n\n' + code;
  evalCode(code);
};
window.share = () => {
  let code = editor.state.doc.toString().trim();
  code = btoa(code);
  url.searchParams.set('src', code);
  window.location = url;
};
window.blankAOC = () => {
  const code = btoa(aocDoc);
  const url = new URL(window.location);
  url.searchParams.set('src', code);
  url.searchParams.set('boilerplate', aocBoilerplateUrl);
  url.searchParams.set('repl', true);

  window.location = url;
}

window.changeREPL = (target) => {
  document.getElementById('result').innerText = '';
  if (target.checked) {
    repl = true;
    compile();
  } else {
    repl = false;
    compile();
  }
  url.searchParams.set('repl', repl);
  window.history.replaceState(null, null, url);
};
if (repl) {
  document.getElementById('replCheckBox').checked = true;
}
compile();
